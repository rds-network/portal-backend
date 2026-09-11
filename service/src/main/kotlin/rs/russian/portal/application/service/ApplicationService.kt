package rs.russian.portal.application.service

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.generated.model.ApplicationDto
import rs.russian.generated.model.ApplicationsFilter
import rs.russian.generated.model.NoteDto
import rs.russian.generated.model.PageRequest
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus.DENY
import rs.russian.portal.application.domain.ApplicationStatus.DONE
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.domain.specification.searchSpecification
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.application.repository.ApplicationRepository
import rs.russian.portal.note.domain.Note
import rs.russian.portal.note.domain.enums.EntityType
import rs.russian.portal.note.service.NoteService
import rs.russian.portal.shared.exception.InvalidRequestException
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.jpa.convert
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.domain.Account
import rs.russian.portal.user.domain.enums.UserGroup.ADMIN_VOLUNTEER
import rs.russian.portal.user.domain.enums.UserGroup.INTERVIEWER
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.AccountService
import java.util.*

@Service
class ApplicationService(
    private val noteService: NoteService,
    private val entityManager: EntityManager,
    private val accountService: AccountService,
    private val applicationMapper: ApplicationMapper,
    private val applicationRepository: ApplicationRepository,
    private val accountRepository: AccountRepository,
) {

    @Transactional
    fun create(request: ApplicationDto): Application {
        val email = request.email!!
        val name = request.name!!
        val existByEmail = applicationRepository.findByEmailAndStatusNotIn(email, listOf(DONE, DENY))
        if (existByEmail.isPresent) {
            return existByEmail.get()
        }
        if (!request.passport.isNullOrBlank()) {
            val existByEmailAndPassport = applicationRepository.findByEmailAndPassportAndStatusNotIn(
                email,
                request.passport!!,
                listOf(DONE, DENY)
            )
            if (existByEmailAndPassport.isPresent) {
                return existByEmailAndPassport.get()
            }
        }
        val application = Application(email = email, name = name)
        applicationMapper.toEntity(request, application)
        normalizeProgramAndProject(application)
        val existUser = accountService.findAccountByEmail(email)
        if (existUser != null) {
            application.type = ApplicationType.PROLONGATION
        }
        return applicationRepository.save(application)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Application {
        return applicationRepository.findById(id).orElseThrow()
    }

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Application {
        return applicationRepository.findByEmail(email).orElseThrow()
    }

    @Transactional(readOnly = true)
    fun getAll(searchQuery: String?, pageRequest: PageRequest, filter: ApplicationsFilter?): Page<Application> {
        val specification = searchSpecification(searchQuery, filter)
        return getAllFull(specification, convert(pageRequest))
    }

    @Transactional
    fun update(applicationDto: ApplicationDto): Application {
        val application = get(applicationDto.id)
        applicationMapper.update(applicationDto, application)
        normalizeProgramAndProject(application)
        if (application.status == DONE && application.contractFrom == null) {
            throw InvalidRequestException("Contract dates not specified")
        }
        if (application.status == DONE && (application.program == null || application.project == null)) {
            throw InvalidRequestException("Program and project must be specified before completing the application")
        }
        return applicationRepository.save(application)
    }

    @Transactional(readOnly = true)
    fun getAssignees(): List<Account> {
        val logins = listOf(ADMIN_VOLUNTEER, INTERVIEWER)
            .flatMap { accountRepository.findAllActiveUsernamesByGroup(it.name) }
            .distinct()
        return accountService.resolve(logins).sortedBy { it.fullName }
    }

    @Transactional
    fun assign(id: UUID, login: String?): Application {
        val application = get(id)
        val account = login?.let {
            accountService.findAccountByLogin(it) ?: throw InvalidRequestException("Employee not found")
        }
        if (account != null && (!account.active || account.groups.none { it == ADMIN_VOLUNTEER || it == INTERVIEWER })) {
            throw InvalidRequestException("Assignee must be an active application employee")
        }
        application.assignee = account?.username
        return applicationRepository.save(application)
    }

    @Transactional
    fun save(application: Application): Application {
        return applicationRepository.save(application)
    }

    @Transactional
    fun addNote(applicationId: UUID, noteDto: NoteDto): Note {
        val application = get(applicationId)
        val currentAccount = accountService.getAccountByLogin(currentUserLogin() ?: throw NotAuthorizedException())
        val note = noteService.save(
            Note(
                createdBy = currentAccount.username,
                entityId = applicationId,
                entityType = EntityType.APPLICATION,
                text = noteDto.text
            )
        )
        application.notes.add(note)
        return note
    }

    /**
     * Получить список заявок с использованием EntityGraph
     * Решает проблему, при которой невозможно одновременная работа Pageable и EntityGraph:
     * HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
     * entityManager.detach(...) - необходим потому что findAll загружает "легковесные" объекты и
     * сохраняет их в контекст, а findAllByIdIn уже загрузит полные объекты, однако если не очистить
     * контекст, то они не будут перезаписаны в контексте, что повлечет дополнительные SQL запросы
     */
    private fun getAllFull(specification: Specification<Application>, pageable: Pageable): Page<Application> {
        val applications = applicationRepository.findAll(specification, pageable)
        applications.forEach { account -> entityManager.detach(account) }
        val applicationsFull = applicationRepository.findAllByIdIn(applications.mapNotNull { it.id }, pageable.sort)
        return PageImpl(applicationsFull, applications.pageable, applications.totalElements)
    }

    private fun normalizeProgramAndProject(application: Application) {
        val projectProgram = application.project?.program
        if (application.project != null) {
            if (application.program == null) {
                application.program = projectProgram
            } else if (application.program?.code != projectProgram?.code) {
                throw InvalidRequestException("Project does not belong to program")
            }
        }
    }
}
