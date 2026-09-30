package rs.russian.portal.talent.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.inbox.service.InboxService
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.talent.api.TalentPostCreateRequest
import rs.russian.portal.talent.api.TalentPostDto
import rs.russian.portal.talent.api.TalentResponseCreateRequest
import rs.russian.portal.talent.api.TalentResponseDto
import rs.russian.portal.talent.api.TalentSkillsDto
import rs.russian.portal.talent.api.TalentSkillsUpdateRequest
import rs.russian.portal.talent.domain.TalentPost
import rs.russian.portal.talent.domain.TalentResponse
import rs.russian.portal.talent.domain.UserSkill
import rs.russian.portal.talent.domain.enums.TalentPostStatus
import rs.russian.portal.talent.domain.enums.TalentPostType
import rs.russian.portal.talent.repository.TalentPostRepository
import rs.russian.portal.talent.repository.TalentPostSpecs
import rs.russian.portal.talent.repository.TalentResponseRepository
import rs.russian.portal.talent.repository.UserSkillRepository
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.time.OffsetDateTime
import java.util.UUID

@Service
class TalentService(
    private val talentPostRepository: TalentPostRepository,
    private val talentResponseRepository: TalentResponseRepository,
    private val userSkillRepository: UserSkillRepository,
    private val programRepository: ProgramRepository,
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val inboxService: InboxService,
) {

    @Transactional(readOnly = true)
    fun listPosts(
        type: TalentPostType?,
        q: String?,
        city: String?,
        programCode: String?,
        pageable: Pageable,
    ): Page<TalentPostDto> {
        val account = requireActiveAccount()
        val query = q?.trim()?.takeIf { it.isNotEmpty() }
        val cityFilter = city?.trim()?.takeIf { it.isNotEmpty() }
        val programFilter = programCode?.trim()?.takeIf { it.isNotEmpty() }
        val sorted = PageRequest.of(
            pageable.pageNumber,
            pageable.pageSize,
            Sort.by(Sort.Direction.DESC, "createdAt"),
        )
        val page = talentPostRepository.findAll(
            TalentPostSpecs.search(
                type = type,
                status = TalentPostStatus.OPEN,
                q = query,
                city = cityFilter,
                programCode = programFilter,
            ),
            sorted,
        )
        val names = loadFullNames(page.content.map { it.authorUsername })
        val responders = page.content.associate { post ->
            post.id!! to talentResponseRepository.findTop5ByPost_IdOrderByCreatedAtDesc(post.id!!)
                .map { it.authorUsername }
                .reversed()
        }
        return page.map { post ->
            toPostDto(
                post = post,
                actor = account,
                authorFullName = names[post.authorUsername.lowercase()],
                responderUsernames = responders[post.id] ?: emptyList(),
            )
        }
    }

    @Transactional
    fun createPost(request: TalentPostCreateRequest): TalentPostDto {
        val account = requireActiveAccount()
        val title = request.title.trim()
        val body = request.body.trim()
        if (title.isEmpty()) throw InvalidRequestException("title is required")
        if (title.length > 200) throw InvalidRequestException("title is too long")
        if (body.isEmpty()) throw InvalidRequestException("body is required")

        val program = request.programCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            programRepository.findByCode(code)
                ?: throw InvalidRequestException("Program '$code' not found")
        }
        val now = OffsetDateTime.now()
        val post = talentPostRepository.save(
            TalentPost(
                type = request.type,
                status = TalentPostStatus.OPEN,
                title = title,
                body = body,
                city = request.city?.trim()?.takeIf { it.isNotEmpty() },
                program = program,
                skills = encodeSkills(request.skills),
                authorUsername = account.username,
                createdAt = now,
                updatedAt = now,
                responseCount = 0,
            )
        )
        val typeLabel = when (post.type) {
            TalentPostType.NEED_PEOPLE -> "ищут людей"
            TalentPostType.CAN_HELP -> "могут помочь"
            TalentPostType.PROJECT_IDEA -> "идея проекта"
        }
        try {
            val recipients = accountRepository.findAllActiveUsernames()
            inboxService.notifyTalentNewPost(
                subject = "Идеи и таланты: $title",
                body = "${account.fullName ?: account.username} разместил(а) объявление ($typeLabel):\n«$title»\n\n/ideas?post=${post.id}",
                createdBy = account.username,
                recipients = recipients,
            )
        } catch (ex: Exception) {
            // Post must stay published even if inbox blast fails.
            org.slf4j.LoggerFactory.getLogger(TalentService::class.java)
                .warn("Could not broadcast talent post {} to inbox", post.id, ex)
        }
        return toPostDto(post, account, account.fullName, emptyList())
    }

    /**
     * One-shot for posts created before inbox broadcast existed.
     * Safe to call repeatedly — skips posts that already have a TALENT_POST notice.
     */
    @Transactional
    fun backfillInboxForOpenPosts(): Int {
        val open = talentPostRepository.findAll(
            TalentPostSpecs.search(
                type = null,
                status = TalentPostStatus.OPEN,
                q = null,
                city = null,
                programCode = null,
            ),
        )
        if (open.isEmpty()) return 0
        val recipients = accountRepository.findAllActiveUsernames()
        var sent = 0
        open.forEach { post ->
            if (inboxService.hasTalentPostNotice(post.id!!)) return@forEach
            val typeLabel = when (post.type) {
                TalentPostType.NEED_PEOPLE -> "ищут людей"
                TalentPostType.CAN_HELP -> "могут помочь"
                TalentPostType.PROJECT_IDEA -> "идея проекта"
            }
            val authorName = accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName
                ?: post.authorUsername
            inboxService.notifyTalentNewPost(
                subject = "Идеи и таланты: ${post.title}",
                body = "$authorName разместил(а) объявление ($typeLabel):\n«${post.title}»\n\n/ideas?post=${post.id}",
                createdBy = post.authorUsername,
                recipients = recipients,
            )
            sent += 1
        }
        return sent
    }

    @Transactional(readOnly = true)
    fun getPost(id: UUID): TalentPostDto {
        val account = requireActiveAccount()
        val post = loadPost(id)
        val responders = talentResponseRepository.findTop5ByPost_IdOrderByCreatedAtDesc(id)
            .map { it.authorUsername }
            .reversed()
        val authorName = accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName
        return toPostDto(post, account, authorName, responders)
    }

    @Transactional(readOnly = true)
    fun listMyPosts(
        status: TalentPostStatus?,
        pageable: Pageable,
    ): Page<TalentPostDto> {
        val account = requireActiveAccount()
        val sorted = PageRequest.of(
            pageable.pageNumber,
            pageable.pageSize,
            Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"),
        )
        val page = talentPostRepository.findAll(
            TalentPostSpecs.mine(account.username, status),
            sorted,
        )
        val responders = page.content.associate { post ->
            post.id!! to talentResponseRepository.findTop5ByPost_IdOrderByCreatedAtDesc(post.id!!)
                .map { it.authorUsername }
                .reversed()
        }
        return page.map { post ->
            toPostDto(
                post = post,
                actor = account,
                authorFullName = account.fullName,
                responderUsernames = responders[post.id] ?: emptyList(),
            )
        }
    }

    @Transactional
    fun closePost(id: UUID): TalentPostDto {
        val account = requireActiveAccount()
        val post = loadPost(id)
        if (!canClose(post, account)) throw NotAuthorizedException()
        if (post.status == TalentPostStatus.CLOSED) {
            return toPostDto(post, account, accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName)
        }
        post.status = TalentPostStatus.CLOSED
        post.updatedAt = OffsetDateTime.now()
        return toPostDto(post, account, accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName)
    }

    @Transactional
    fun reopenPost(id: UUID): TalentPostDto {
        val account = requireActiveAccount()
        val post = loadPost(id)
        if (!canClose(post, account)) throw NotAuthorizedException()
        if (post.status == TalentPostStatus.OPEN) {
            return toPostDto(post, account, accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName)
        }
        post.status = TalentPostStatus.OPEN
        post.updatedAt = OffsetDateTime.now()
        return toPostDto(post, account, accountRepository.findByUsername(post.authorUsername).orElse(null)?.fullName)
    }

    @Transactional
    fun createResponse(id: UUID, request: TalentResponseCreateRequest): TalentResponseDto {
        val account = requireActiveAccount()
        val post = loadPost(id)
        if (post.status != TalentPostStatus.OPEN) {
            throw InvalidRequestException("Post is closed")
        }
        if (post.authorUsername.equals(account.username, ignoreCase = true)) {
            throw InvalidRequestException("Cannot respond to your own post")
        }
        val message = request.message.trim()
        if (message.isEmpty()) throw InvalidRequestException("message is required")
        if (message.length > 2000) throw InvalidRequestException("message is too long")
        if (talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(id, account.username)) {
            throw InvalidRequestException("Already responded")
        }

        val saved = talentResponseRepository.save(
            TalentResponse(
                post = post,
                authorUsername = account.username,
                message = message,
            )
        )
        post.responseCount = post.responseCount + 1
        post.updatedAt = OffsetDateTime.now()

        val typeLabel = when (post.type) {
            TalentPostType.NEED_PEOPLE -> "ищут людей"
            TalentPostType.CAN_HELP -> "могут помочь"
            TalentPostType.PROJECT_IDEA -> "идея проекта"
        }
        inboxService.notifyTalentResponse(
            recipient = post.authorUsername,
            subject = "Отклик: ${post.title}",
            body = "${account.fullName} (@${account.username}) откликнулся на объявление ($typeLabel):\n\n" +
                "«${post.title}»\n\n$message\n\n/ideas?post=${post.id}",
            createdBy = account.username,
        )

        return TalentResponseDto(
            id = saved.id!!,
            postId = id,
            authorUsername = account.username,
            authorFullName = account.fullName,
            message = saved.message,
            createdAt = saved.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResponses(id: UUID): List<TalentResponseDto> {
        val account = requireActiveAccount()
        val post = loadPost(id)
        if (!canViewResponses(post, account)) throw NotAuthorizedException()
        val responses = talentResponseRepository.findAllByPost_IdOrderByCreatedAtAsc(id)
        val names = loadFullNames(responses.map { it.authorUsername })
        return responses.map {
            TalentResponseDto(
                id = it.id!!,
                postId = id,
                authorUsername = it.authorUsername,
                authorFullName = names[it.authorUsername.lowercase()],
                message = it.message,
                createdAt = it.createdAt,
            )
        }
    }

    /**
     * Nav badge: open posts by others (all, or only after [since]) plus new responses on my posts after [since].
     * Frontend stores last visit and passes it as [since]; badge clears after opening /ideas.
     */
    @Transactional(readOnly = true)
    fun unreadCount(since: OffsetDateTime?): Long {
        val account = requireActiveAccount()
        val me = account.username
        val newPosts = if (since != null) {
            talentPostRepository.countByStatusAndAuthorUsernameNotIgnoreCaseAndCreatedAtAfter(
                TalentPostStatus.OPEN,
                me,
                since,
            )
        } else {
            talentPostRepository.countByStatusAndAuthorUsernameNotIgnoreCase(
                TalentPostStatus.OPEN,
                me,
            )
        }
        val newResponses = if (since != null) {
            talentResponseRepository.countNewForPostOwner(me, since)
        } else {
            0L
        }
        return newPosts + newResponses
    }

    @Transactional(readOnly = true)
    fun getMySkills(): TalentSkillsDto {
        val account = requireActiveAccount()
        val accountId = account.id ?: throw InvalidRequestException("Account id missing")
        return TalentSkillsDto(
            skills = userSkillRepository.findAllByAccountIdOrderBySkillAsc(accountId).map { it.skill },
        )
    }

    @Transactional
    fun putMySkills(request: TalentSkillsUpdateRequest): TalentSkillsDto {
        val account = requireActiveAccount()
        val accountId = account.id ?: throw InvalidRequestException("Account id missing")
        val normalized = normalizeSkills(request.skills)
        userSkillRepository.deleteAllByAccountId(accountId)
        userSkillRepository.flush()
        normalized.forEach { skill ->
            userSkillRepository.save(UserSkill(accountId = accountId, skill = skill))
        }
        return TalentSkillsDto(skills = normalized)
    }

    private fun loadPost(id: UUID): TalentPost =
        talentPostRepository.findByIdWithProgram(id)
            .orElseThrow { EntityNotFoundException("Talent post $id not found") }

    private fun requireActiveAccount(): Account {
        currentUserLogin() ?: throw NotAuthorizedException()
        val account = accountService.getCurrentAccount()
        if (!account.active) throw NotAuthorizedException()
        return account
    }

    private fun canClose(post: TalentPost, account: Account): Boolean =
        post.authorUsername.equals(account.username, ignoreCase = true) || isManager(account.groups)

    private fun canViewResponses(post: TalentPost, account: Account): Boolean =
        post.authorUsername.equals(account.username, ignoreCase = true) || isManager(account.groups)

    private fun isManager(groups: Set<UserGroup>): Boolean =
        groups.any { it == ADMIN || it == ADMIN_VOLUNTEER || it == MAIN_VOLUNTEER || it == ADMIN_SSO }

    private fun toPostDto(
        post: TalentPost,
        actor: Account,
        authorFullName: String?,
        responderUsernames: List<String> = emptyList(),
    ): TalentPostDto {
        val already = talentResponseRepository.existsByPost_IdAndAuthorUsernameIgnoreCase(
            post.id!!,
            actor.username,
        )
        return TalentPostDto(
            id = post.id!!,
            type = post.type,
            status = post.status,
            title = post.title,
            body = post.body,
            city = post.city,
            programCode = post.program?.code,
            programNameRu = post.program?.nameRu,
            skills = decodeSkills(post.skills),
            authorUsername = post.authorUsername,
            authorFullName = authorFullName,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            responseCount = post.responseCount,
            responderUsernames = responderUsernames,
            mine = post.authorUsername.equals(actor.username, ignoreCase = true),
            alreadyResponded = already,
        )
    }

    private fun loadFullNames(usernames: Collection<String>): Map<String, String> {
        val unique = usernames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return emptyMap()
        return accountRepository.findAllByUsernameIn(unique)
            .associate { it.username.lowercase() to it.fullName }
    }

    companion object {
        fun encodeSkills(skills: List<String>?): String? {
            val normalized = normalizeSkills(skills ?: emptyList())
            return normalized.takeIf { it.isNotEmpty() }?.joinToString(",")
        }

        fun decodeSkills(raw: String?): List<String> =
            raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

        fun normalizeSkills(skills: List<String>): List<String> =
            skills
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.length > 80) it.take(80) else it }
                .distinctBy { it.lowercase() }
                .take(30)
    }
}
