package rs.russian.portal.announcement.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import rs.russian.portal.announcement.domain.Announcement
import java.util.UUID

@Repository
interface AnnouncementRepository : JpaRepository<Announcement, UUID> {

    @Query("""
        SELECT * FROM announcement
        WHERE active = true
          AND COALESCE(banner, false) = false
          AND (
            audience = 'ALL'
            OR (audience = 'PROGRAM' AND program_code = :programCode)
            OR (audience = 'USER' AND target_username = :username)
          )
        ORDER BY create_time DESC
    """, nativeQuery = true)
    fun findForUser(
        @Param("programCode") programCode: String?,
        @Param("username") username: String,
    ): List<Announcement>

    @Query("""
        SELECT COUNT(*) FROM announcement a
        LEFT JOIN announcement_read ar
            ON ar.announcement_id = a.id AND ar.account_id = :accountId
        WHERE a.active = true
          AND COALESCE(a.banner, false) = false
          AND (
            a.audience = 'ALL'
            OR (a.audience = 'PROGRAM' AND a.program_code = :programCode)
            OR (a.audience = 'USER' AND a.target_username = :username)
          )
          AND ar.announcement_id IS NULL
    """, nativeQuery = true)
    fun countUnreadForUser(
        @Param("programCode") programCode: String?,
        @Param("username") username: String,
        @Param("accountId") accountId: Int,
    ): Long

    @Query("""
        SELECT a.* FROM announcement a
        LEFT JOIN announcement_read ar
            ON ar.announcement_id = a.id AND ar.account_id = :accountId
        WHERE a.active = true
          AND COALESCE(a.banner, false) = true
          AND (
            a.audience = 'ALL'
            OR (a.audience = 'PROGRAM' AND a.program_code = :programCode)
            OR (a.audience = 'USER' AND a.target_username = :username)
          )
          AND ar.announcement_id IS NULL
        ORDER BY a.create_time DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findLatestUnreadBanner(
        @Param("programCode") programCode: String?,
        @Param("username") username: String,
        @Param("accountId") accountId: Int,
    ): Announcement?

    @Query("""
        SELECT * FROM announcement
        WHERE active = true
        ORDER BY create_time DESC
        LIMIT 200
    """, nativeQuery = true)
    fun findAllForManage(): List<Announcement>
}
