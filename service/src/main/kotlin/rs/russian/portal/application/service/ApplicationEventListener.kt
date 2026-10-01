package rs.russian.portal.application.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import rs.russian.generated.model.ContractDto
import rs.russian.portal.accountstatus.domain.enums.AccountStatusEventSource
import rs.russian.portal.accountstatus.service.AccountStatusService
import rs.russian.portal.application.domain.Application
import rs.russian.portal.application.domain.ApplicationStatus
import rs.russian.portal.application.domain.ApplicationType
import rs.russian.portal.application.event.ApplicationCreatedEvent
import rs.russian.portal.application.event.ApplicationUpdateEvent
import rs.russian.portal.application.mapper.ApplicationMapper
import rs.russian.portal.mail.service.EmailService
import rs.russian.portal.user.mapper.ContractMapper
import rs.russian.portal.user.service.AccountInviteService
import rs.russian.portal.user.service.AccountService
import java.util.UUID

@Component
class ApplicationEventListener(
    private val emailService: EmailService,
    private val accountService: AccountService,
    private val accountInviteService: AccountInviteService,
    private val accountStatusService: AccountStatusService,
    private val contractMapper: ContractMapper,
    private val templateEngine: TemplateEngine,
    private val applicationMapper: ApplicationMapper,
    private val applicationService: ApplicationService,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    fun handleApplicationCreate(event: ApplicationCreatedEvent) {
        val application = applicationService.get(event.id)
        val message = templateEngine.process(
            "application_received",
            Context().also { it.setVariables(mapOf("id" to application.id)) },
        )
        emailService.sendCommonEmail(
            application.email,
            "Ваша анкета получена",
            message,
            "Русская Диаспора <apply@russian.rs>",
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(fallbackExecution = true)
    fun handleApplicationStatusChange(event: ApplicationUpdateEvent) {
        val application = applicationService.get(event.id)
        if (application.status != ApplicationStatus.DONE) return

        try {
            when (application.type) {
                ApplicationType.NEW -> provisionNewVolunteer(application)
                ApplicationType.PROLONGATION -> prolongVolunteer(application)
            }
        } catch (ex: Exception) {
            log.error(
                "Failed to provision account after application {} reached DONE (type={}, email={})",
                application.id,
                application.type,
                application.email,
                ex,
            )
            throw ex
        }
    }

    private fun provisionNewVolunteer(application: Application) {
        val contractFrom = application.contractFrom
            ?: error("contractFrom missing for DONE application ${application.id}")
        val contractUntil = application.contractUntil
            ?: error("contractUntil missing for DONE application ${application.id}")
        val contractType = application.contractType
            ?: error("contractType missing for DONE application ${application.id}")

        val existing = accountService.findAccountByEmail(application.email)
        val account = if (existing == null) {
            accountService.create(application.email, application.name).also {
                log.info(
                    "Created portal account {} for NEW application {}",
                    it.username,
                    application.id,
                )
            }
        } else {
            log.warn(
                "NEW application {} completed but account already exists for {} — updating profile and re-sending invite",
                application.id,
                application.email,
            )
            existing
        }

        accountService.updateContracts(
            account.id!!,
            setOf(
                ContractDto(
                    id = UUID.randomUUID(),
                    startDate = contractFrom,
                    endDate = contractUntil,
                    type = contractType,
                ),
            ),
        )
        accountService.updateInfo(account.id!!, applicationMapper.mapToInfo(application, account))
        updateProgramAndProject(application, account.id!!)

        // Explicit invite here — do not rely on nested TransactionalEventListener(UserCreatedEvent),
        // which was silently dropped when account creation ran inside this AFTER_COMMIT handler.
        try {
            accountInviteService.sendWelcomeEmail(account)
        } catch (ex: Exception) {
            log.error(
                "Account provisioned for application {} but welcome email failed ({})",
                application.id,
                account.email,
                ex,
            )
        }
    }

    private fun prolongVolunteer(application: Application) {
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
        val contractFrom = application.contractFrom
            ?: error("contractFrom missing for DONE application ${application.id}")
        val contractUntil = application.contractUntil
            ?: error("contractUntil missing for DONE application ${application.id}")
        val contractType = application.contractType
            ?: error("contractType missing for DONE application ${application.id}")

        accountStatusService.applyImmediate(
            accountId = account.id!!,
            activeTo = true,
            source = AccountStatusEventSource.DIRECT,
            actorUsername = null,
            reason = "application prolongation",
            notifyApprover = true,
        )
        val contracts = contractMapper.map(account.contracts)
        contracts.add(
            ContractDto(
                id = UUID.randomUUID(),
                startDate = contractFrom,
                endDate = contractUntil,
                type = contractType,
            ),
        )
        accountService.updateContracts(account.id!!, contracts)
        updateProgramAndProject(application, account.id!!)
    }

    private fun updateProgramAndProject(
        application: Application,
        accountId: Int,
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
