package rs.russian.portal.accountstatus.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rs.russian.portal.accountstatus.domain.AccountStatusEvent
import java.util.UUID

interface AccountStatusEventRepository : JpaRepository<AccountStatusEvent, UUID> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<AccountStatusEvent>
}
