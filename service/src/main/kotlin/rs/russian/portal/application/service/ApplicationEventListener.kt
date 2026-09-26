package rs.russian.portal.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.portal.application.domain.Application
import rs.russian.generated.model.ContractDto
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.event.ApplicationCreatedEvent
import rs.russian.portal.application.event.ApplicationUpdateEvent
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.telegram.TelegramNotificationService
import rs.russian.portal.user.mapper.ContractMapper
import rs.russian.portal.user.service.AccountService
import java.util.*

@Component
class ApplicationEventListener(
    private val emailService: EmailService,
    private val accountService: AccountService,
    private val contractMapper: ContractMapper,
    private val templateEngine: TemplateEngine,
    private val applicationMapper: ApplicationMapper,
    private val applicationService: ApplicationService,
    private val telegramNotificationService: TelegramNotificationService,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    fun handleApplicationCreate(event: ApplicationCreatedEvent) {
        val application = applicationService.get(event.id)
        val message = templateEngine.process(
            "application_received",
            Context().also { it.setVariables(mapOf("id" to application.id)) })
        emailService.sendCommonEmail(
            application.email,
            "Ваша анкета получена",
            message,
            "Русская Диаспора <apply@russian.rs>"
        )
        try {
            telegramNotificationService.notifyNewApplication(
                id = application.id!!.toString(),
                name = application.name,
                email = application.email,
                type = application.type.name,
            )
        } catch (ex: Exception) {
            log.warn("Telegram alert for application {} failed: {}", application.id, ex.message)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    fun handleApplicationStatusChange(event: ApplicationUpdateEvent) {
        val application = applicationService.get(event.id)
        if (application.status == ApplicationStatus.DONE) {
            if (application.type == ApplicationType.NEW) {
                if (accountService.findAccountByEmail(application.email) == null) {
                    val account = accountService.create(application.email, application.name)
                    accountService.updateContracts(
                        account.id!!,
                        setOf(
                            ContractDto(
                                id = UUID.randomUUID(),
                                startDate = application.contractFrom!!,
                                endDate = application.contractUntil!!,
                                type = application.contractType!!
                            )
                        )
                    )
                    accountService.updateInfo(account.id!!, applicationMapper.mapToInfo(application, account))
                    updateProgramAndProject(application, account.id!!)
                }
            }
            if (application.type == ApplicationType.PROLONGATION) {
                val account = accountService.findAccountByEmail(application.email)
                if (account == null) {
                    // The account this prolongation targeted no longer resolves by email — it was
                    // depersonalized (email replaced by a sentinel) while the application sat pending.
                    // Nothing to re-activate; skip rather than NPE on the removed identity.
                    log.warn(
                        "Prolongation application {} references a missing/depersonalized account, skipping",
                        application.id,
                    )
                    return
                }
                accountService.switchActiveState(account.id!!, true)
                val contracts = contractMapper.map(account.contracts)
                contracts.add(
                    ContractDto(
                        id = UUID.randomUUID(),
                        startDate = application.contractFrom!!,
                        endDate = application.contractUntil!!,
                        type = application.contractType!!
                    )
                )
                accountService.updateContracts(account.id!!, contracts)
                updateProgramAndProject(application, account.id!!)
            }
        }
    }

    private fun updateProgramAndProject(
        application: Application,
        accountId: Int
    ) {
        when {
            application.project != null -> accountService.setProject(accountId, application.project!!.code)
            application.program != null -> accountService.setProgram(accountId, application.program!!.code)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApplicationEventListener::class.java)
    }
}
