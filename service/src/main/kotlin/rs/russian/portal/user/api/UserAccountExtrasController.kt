package rs.russian.portal.user.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rs.russian.generated.model.UserInfoDto
import rs.russian.portal.user.mapper.UserMapper
import rs.russian.portal.user.repository.AccountRepository
import rs.russian.portal.user.service.SecondaryProgramService
import rs.russian.portal.shared.exception.NotAuthorizedException
import rs.russian.portal.shared.security.currentUserLogin

@RestController
@RequestMapping("/user/account")
class UserAccountExtrasController(
    private val secondaryProgramService: SecondaryProgramService,
    private val accountRepository: AccountRepository,
    private val userMapper: UserMapper,
) {

    @GetMapping("/{id}/secondary-programs")
    fun getSecondaryPrograms(@PathVariable id: Int): ResponseEntity<List<String>> =
        ResponseEntity.ok(secondaryProgramService.list(id))

    @PutMapping("/{id}/secondary-programs")
    fun putSecondaryPrograms(
        @PathVariable id: Int,
        @RequestBody programCodes: List<String>,
    ): ResponseEntity<List<String>> =
        ResponseEntity.ok(secondaryProgramService.replace(id, programCodes))

    /**
     * Активные аккаунты, у которых текущий пользователь назначен принудительным контролёром отчётов.
     */
    @GetMapping("/controlled-by-me")
    fun controlledByMe(): ResponseEntity<List<UserInfoDto>> {
        val login = currentUserLogin() ?: throw NotAuthorizedException()
        val accounts = accountRepository.findAllByReportControllerUsernameIgnoreCaseAndActiveTrue(login)
        return ResponseEntity.ok(accounts.map { userMapper.map(it.info) })
    }
}
