package rs.russian.portal.inbox.repository

interface WarningCountProjection {
    val username: String
    val cnt: Long
}