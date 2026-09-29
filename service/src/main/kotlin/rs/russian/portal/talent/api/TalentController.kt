package rs.russian.portal.talent.api

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.talent.domain.enums.TalentPostType
import rs.russian.portal.talent.service.TalentService
import java.util.UUID

@RestController
@RequestMapping("/talent")
class TalentController(
    private val talentService: TalentService,
) {

    @GetMapping("/posts")
    fun listPosts(
        @RequestParam(required = false) type: TalentPostType?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) programCode: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ResponseEntity<Page<TalentPostDto>> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 50))
        return ResponseEntity.ok(talentService.listPosts(type, q, city, programCode, pageable))
    }

    @PostMapping("/posts")
    fun createPost(@RequestBody request: TalentPostCreateRequest): ResponseEntity<TalentPostDto> =
        ResponseEntity.ok(talentService.createPost(request))

    @GetMapping("/posts/{id}")
    fun getPost(@PathVariable id: UUID): ResponseEntity<TalentPostDto> =
        ResponseEntity.ok(talentService.getPost(id))

    @PostMapping("/posts/{id}/close")
    fun closePost(@PathVariable id: UUID): ResponseEntity<TalentPostDto> =
        ResponseEntity.ok(talentService.closePost(id))

    @PostMapping("/posts/{id}/responses")
    fun createResponse(
        @PathVariable id: UUID,
        @RequestBody request: TalentResponseCreateRequest,
    ): ResponseEntity<TalentResponseDto> =
        ResponseEntity.ok(talentService.createResponse(id, request))

    @GetMapping("/posts/{id}/responses")
    fun listResponses(@PathVariable id: UUID): ResponseEntity<List<TalentResponseDto>> =
        ResponseEntity.ok(talentService.listResponses(id))

    @GetMapping("/me/skills")
    fun mySkills(): ResponseEntity<TalentSkillsDto> =
        ResponseEntity.ok(talentService.getMySkills())

    @PutMapping("/me/skills")
    fun putMySkills(@RequestBody request: TalentSkillsUpdateRequest): ResponseEntity<TalentSkillsDto> =
        ResponseEntity.ok(talentService.putMySkills(request))
}
