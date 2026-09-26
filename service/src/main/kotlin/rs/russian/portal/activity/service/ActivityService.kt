package rs.russian.portal.activity.service

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rs.russian.portal.activity.api.ActivityEventDto
import rs.russian.portal.activity.api.OnlinePersonDto
import rs.russian.portal.activity.api.OnlinePresenceDto
import rs.russian.portal.activity.domain.ActivityEvent
import rs.russian.portal.activity.repository.ActivityEventRepository
import rs.russian.portal.shared.security.currentUserLogin
import rs.russian.portal.user.repository.AccountRepository
import java.time.LocalDateTime

@Service
class ActivityService(
    private val activityEventRepository: ActivityEventRepository,
    private val accountRepository: AccountRepository,
) {

    @Transactional
    fun record(username: String?, ip: String?, method: String, path: String, query: String?) {
        try {
            activityEventRepository.save(
                ActivityEvent(
                    username = username,
                    ip = ip,
                    method = method,
                    path = path,
                    query = query?.take(400),
                    action = label(method, path),
                )
            )
            val login = username?.trim()?.takeIf { it.isNotEmpty() }
            if (login != null) {
                val now = LocalDateTime.now()
                accountRepository.touchLastSeen(login, now, now.minusMinutes(2))
            }
        } catch (ex: Exception) {
            log.debug("Skip activity log for {} {}", method, path, ex)
        }
    }

    @Transactional(readOnly = true)
    fun list(q: String?, sort: String, dir: String, page: Int, size: Int): Page<ActivityEventDto> {
        val field = when (sort) {
            "username" -> "username"
            "ip" -> "ip"
            "path" -> "path"
            "action" -> "action"
            else -> "createTime"
        }
        val order = if (dir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        return activityEventRepository
            .search(q?.trim(), PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(order, field)))
            .map(::toDto)
    }

    @Transactional(readOnly = true)
    fun online(minutes: Int = 10): OnlinePresenceDto {
        val window = minutes.coerceIn(1, 60)
        val since = LocalDateTime.now().minusMinutes(window.toLong())
        val events = activityEventRepository.findSince(
            since,
            PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createTime")),
        )
        val me = currentUserLogin()?.lowercase()
        val latestByKey = LinkedHashMap<String, ActivityEvent>()
        for (event in events) {
            val key = event.username?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
                ?: "guest:${event.ip ?: event.id}"
            if (!latestByKey.containsKey(key)) {
                latestByKey[key] = event
            }
        }
        val logins = latestByKey.values.mapNotNull { it.username?.trim()?.takeIf { login -> login.isNotEmpty() } }
        val names = if (logins.isEmpty()) emptyMap()
        else accountRepository.findAllByUsernameIn(logins).associate { it.username.lowercase() to it.fullName.ifBlank { it.username } }

        val people = latestByKey.values.map { event ->
            val login = event.username?.trim()?.takeIf { it.isNotEmpty() }
            OnlinePersonDto(
                username = login,
                displayName = login?.let { names[it.lowercase()] ?: it } ?: "Гость",
                ip = event.ip,
                path = event.path,
                query = event.query,
                lastSeen = event.createTime,
                self = login != null && me != null && login.equals(me, ignoreCase = true),
            )
        }.sortedWith(
            compareByDescending<OnlinePersonDto> { it.self }
                .thenByDescending { it.lastSeen }
        )

        val loggedIn = people.count { it.username != null }
        val guests = people.size - loggedIn
        return OnlinePresenceDto(
            total = people.size,
            loggedIn = loggedIn,
            guests = guests,
            people = people,
        )
    }

    private fun toDto(item: ActivityEvent) = ActivityEventDto(
        id = item.id!!,
        createTime = item.createTime,
        username = item.username,
        ip = item.ip,
        method = item.method,
        path = item.path,
        query = item.query,
        action = item.action,
        link = uiLink(item.path, item.query),
    )

    companion object {
        private val log = LoggerFactory.getLogger(ActivityService::class.java)

        fun label(method: String, path: String): String = when {
            path.startsWith("/report-overdue") && method == "POST" -> "Рассылка о несдаче"
            path.startsWith("/report-overdue") -> "Список несдачи"
            path.startsWith("/work-assignments") && method == "POST" -> "Создал задачу"
            path.startsWith("/work-assignments") && method == "PATCH" -> "Изменил задачу"
            path.startsWith("/work-assignments") && method == "DELETE" -> "Удалил задачу"
            path.startsWith("/inbox") && method == "POST" -> "Сообщение в ЛК"
            path.startsWith("/announcements") && method == "POST" -> "Рассылка уведомления"
            path.startsWith("/mup-letters") && method == "POST" -> "Письмо в МУП"
            path.startsWith("/org-links") && method == "POST" -> "Добавил ресурс"
            path.contains("/heatmap") || path.contains("HeatMap") || path.contains("heat-map") -> "Тепловая карта"
            path.startsWith("/report") && method == "POST" -> "Сохранил отчёт"
            path.startsWith("/profile") || path.startsWith("/user") -> "Профиль"
            method == "GET" -> "Открыл $path"
            else -> "$method $path"
        }

        fun uiLink(path: String, query: String?): String? {
            val q = query?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
            return when {
                path.startsWith("/report-overdue") -> "/reports/overdue"
                path.startsWith("/work-assignments") -> "/tasks"
                path.startsWith("/inbox") -> "/messages"
                path.startsWith("/announcements") -> "/announcements/admin"
                path.startsWith("/org-links") -> "/resources"
                path.startsWith("/mup-letters") -> "/activity"
                path.contains("heat-map") || path.contains("HeatMap") -> "/volunteers/heatmap$q"
                path.startsWith("/reports") -> "/reports"
                else -> null
            }
        }
    }
}
