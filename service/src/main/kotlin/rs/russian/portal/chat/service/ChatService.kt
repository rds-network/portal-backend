package rs.russian.portal.chat.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.chat.api.ChatCreateProgramRoomRequest
import rs.russian.portal.chat.api.ChatMemberDto
import rs.russian.portal.chat.api.ChatMessageDto
import rs.russian.portal.chat.api.ChatRoomDto
import rs.russian.portal.chat.api.ChatRoomsResponse
import rs.russian.portal.chat.api.ChatSendMessageRequest
import rs.russian.portal.chat.domain.ChatMessage
import rs.russian.portal.chat.domain.ChatRoom
import rs.russian.portal.chat.domain.enums.ChatRoomType
import rs.russian.portal.chat.repository.ChatMessageRepository
import rs.russian.portal.chat.repository.ChatRoomRepository
import rs.russian.portal.program.repository.ProgramCuratorRepository
import rs.russian.portal.program.repository.ProgramRepository
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.PrivilegedOps
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.shared.security.currentUserRoles
import rs.russian.portal.shared.security.realUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_SSO
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.MAIN_VOLUNTEER
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.repository.UserSecondaryProgramRepository
import rs.russian.portal.user.service.AccountService
import java.util.UUID

@Service
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
    private val programRepository: ProgramRepository,
    private val programCuratorRepository: ProgramCuratorRepository,
    private val secondaryProgramRepository: UserSecondaryProgramRepository,
) {

    @Transactional(readOnly = true)
    fun listRooms(): ChatRoomsResponse {
        val account = requireActiveAccount()
        val canSeeAll = canSeeAllRooms(account)
        val canCreate = canCreateProgramRoom(account)
        val allRooms = chatRoomRepository.findAllWithProgram()
        val visible = if (canSeeAll) {
            allRooms
        } else {
            val codes = userProgramCodes(account)
            allRooms.filter { room ->
                room.type == ChatRoomType.GENERAL ||
                    (room.type == ChatRoomType.PROGRAM &&
                        room.program?.code != null &&
                        codes.any { it.equals(room.program!!.code, ignoreCase = true) })
            }
        }
        return ChatRoomsResponse(
            rooms = visible.map { toRoomDto(it) },
            canSeeAll = canSeeAll,
            canCreateProgramRoom = canCreate,
        )
    }

    @Transactional
    fun createProgramRoom(request: ChatCreateProgramRoomRequest): ChatRoomDto {
        val account = requireActiveAccount()
        if (!canCreateProgramRoom(account)) throw NotAuthorizedException()

        val code = request.programCode.trim()
        if (code.isEmpty()) throw InvalidRequestException("programCode is required")

        val program = programRepository.findByCode(code)
            ?: throw InvalidRequestException("Program '$code' not found")

        if (!isManager(account.groups) &&
            !programCuratorRepository.existsByProgramCodeAndUsernameIgnoreCase(program.code, account.username)
        ) {
            throw NotAuthorizedException()
        }

        chatRoomRepository.findByTypeAndProgram_CodeIgnoreCase(ChatRoomType.PROGRAM, program.code)
            .ifPresent { throw InvalidRequestException("Program chat already exists") }

        val title = request.title?.trim().orEmpty().ifEmpty { "Чат · ${program.nameRu}" }
        val room = chatRoomRepository.save(
            ChatRoom(
                type = ChatRoomType.PROGRAM,
                program = program,
                title = title,
                createdBy = account.username,
            )
        )
        return toRoomDto(room)
    }

    @Transactional(readOnly = true)
    fun listMessages(roomId: UUID, afterId: UUID?, limit: Int = DEFAULT_LIMIT): List<ChatMessageDto> {
        val account = requireActiveAccount()
        val room = loadRoom(roomId)
        assertCanAccessRoom(account, room)

        val pageLimit = limit.coerceIn(1, MAX_LIMIT)
        val messages = if (afterId != null) {
            val anchor = chatMessageRepository.findById(afterId)
                .orElseThrow { InvalidRequestException("Unknown afterId") }
            if (anchor.room.id != room.id) throw InvalidRequestException("afterId is from another room")
            chatMessageRepository.findByRoomIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                room.id!!,
                anchor.createdAt,
                PageRequest.of(0, pageLimit),
            )
        } else {
            chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(room.id!!, PageRequest.of(0, pageLimit))
                .asReversed()
        }

        val names = nameMap(messages.map { it.authorUsername })
        return messages.map { toMessageDto(it, account.username, names) }
    }

    @Transactional
    fun sendMessage(roomId: UUID, request: ChatSendMessageRequest): ChatMessageDto {
        val account = requireActiveAccount()
        val room = loadRoom(roomId)
        assertCanAccessRoom(account, room)

        val body = request.body.trim()
        if (body.isEmpty()) throw InvalidRequestException("body is required")
        if (body.length > MAX_BODY) throw InvalidRequestException("body is too long")

        val saved = chatMessageRepository.save(
            ChatMessage(
                room = room,
                authorUsername = account.username,
                body = body,
            )
        )
        return toMessageDto(saved, account.username, mapOf(account.username.lowercase() to account.fullName))
    }

    @Transactional(readOnly = true)
    fun listMembers(roomId: UUID): List<ChatMemberDto> {
        val account = requireActiveAccount()
        val room = loadRoom(roomId)
        assertCanAccessRoom(account, room)

        return when (room.type) {
            ChatRoomType.GENERAL ->
                accountRepository.findActiveAccounts(PageRequest.of(0, GENERAL_MEMBERS_LIMIT))
                    .map { toMemberDto(it) }

            ChatRoomType.PROGRAM -> {
                val code = room.program?.code ?: return emptyList()
                accountRepository.findActiveByProgramCode(code).map { toMemberDto(it) }
            }
        }
    }

    fun assertCanAccessRoom(account: Account, room: ChatRoom) {
        if (!account.active) throw NotAuthorizedException()
        if (canSeeAllRooms(account)) return
        when (room.type) {
            ChatRoomType.GENERAL -> return
            ChatRoomType.PROGRAM -> {
                val code = room.program?.code ?: throw NotAuthorizedException()
                if (!userProgramCodes(account).any { it.equals(code, ignoreCase = true) }) {
                    throw NotAuthorizedException()
                }
            }
        }
    }

    private fun requireActiveAccount(): Account {
        currentUserLogin() ?: throw NotAuthorizedException()
        val account = accountService.getCurrentAccount()
        if (!account.active) throw NotAuthorizedException()
        return account
    }

    private fun loadRoom(roomId: UUID): ChatRoom =
        chatRoomRepository.findByIdWithProgram(roomId)
            .orElseThrow { EntityNotFoundException("Chat room $roomId not found") }

    private fun canSeeAllRooms(account: Account): Boolean {
        val roles = currentUserRoles() ?: account.groups
        if (roles.contains(ADMIN_SSO)) return true
        return PrivilegedOps.isAllowed(realUserLogin() ?: account.username, roles, account)
    }

    private fun canCreateProgramRoom(account: Account): Boolean {
        if (isManager(account.groups)) return true
        return programCuratorRepository.existsByUsernameIgnoreCase(account.username)
    }

    private fun isManager(groups: Set<UserGroup>): Boolean =
        groups.any { it == ADMIN || it == ADMIN_VOLUNTEER || it == MAIN_VOLUNTEER || it == ADMIN_SSO }

    private fun userProgramCodes(account: Account): Set<String> {
        val codes = mutableSetOf<String>()
        account.info?.program?.code?.let { codes += it }
        account.id?.let { id ->
            secondaryProgramRepository.findAllByAccountIdOrderByProgramCodeAsc(id)
                .forEach { codes += it.programCode }
        }
        return codes
    }

    private fun toRoomDto(room: ChatRoom): ChatRoomDto =
        ChatRoomDto(
            id = room.id!!,
            type = room.type.name,
            title = room.title,
            programCode = room.program?.code,
            programNameRu = room.program?.nameRu,
            programNameEn = room.program?.nameEn,
            programNameSr = room.program?.nameSr,
            createdAt = room.createdAt,
        )

    private fun toMessageDto(
        message: ChatMessage,
        currentUsername: String,
        names: Map<String, String>,
    ): ChatMessageDto =
        ChatMessageDto(
            id = message.id!!,
            roomId = message.room.id!!,
            authorUsername = message.authorUsername,
            authorFullName = names[message.authorUsername.lowercase()],
            body = message.body,
            createdAt = message.createdAt,
            mine = message.authorUsername.equals(currentUsername, ignoreCase = true),
        )

    private fun toMemberDto(account: Account): ChatMemberDto =
        ChatMemberDto(
            username = account.username,
            fullName = account.fullName,
            programCode = account.info?.program?.code,
        )

    private fun nameMap(usernames: Collection<String>): Map<String, String> {
        if (usernames.isEmpty()) return emptyMap()
        return accountRepository.findAllByUsernameIn(usernames.distinct())
            .associate { it.username.lowercase() to it.fullName }
    }

    companion object {
        const val DEFAULT_LIMIT = 80
        const val MAX_LIMIT = 200
        const val MAX_BODY = 4000
        const val GENERAL_MEMBERS_LIMIT = 500
    }
}
