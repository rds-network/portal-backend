package rs.russian.portal.shared.audit

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.shared.jpa.JpaEntity
import rs.russian.portal.shared.security.realUserLogin

@Component
class AuditEntityListener(
    private val applicationContext: ApplicationContext
) {

    @PostPersist
    @Transactional(propagation = REQUIRES_NEW)
    fun onPostInsert(entity: JpaEntity<*>) {
        saveAuditLog(entity, AuditOperation.INSERT)
    }

    @PostUpdate
    @Transactional(propagation = REQUIRES_NEW)
    fun onPostUpdate(entity: JpaEntity<*>) {
        saveAuditLog(entity, AuditOperation.UPDATE)
    }

    @PostRemove
    @Transactional(propagation = REQUIRES_NEW)
    fun onPostDelete(entity: JpaEntity<*>) {
        saveAuditLog(entity, AuditOperation.DELETE)
    }

    private fun saveAuditLog(entity: JpaEntity<*>, operation: AuditOperation) {
        val entityName = entity.javaClass.simpleName
        val entityId = entity.id.toString()

        val auditLog = AuditLog(
            entityType = entityName.uppercase(),
            entityId = entityId,
            operation = operation,
            userLogin = realUserLogin() ?: "",
            data = entity.toString()
        )

        applicationContext.getBean(AuditRepository::class.java).saveAndFlush(auditLog)
    }
}
