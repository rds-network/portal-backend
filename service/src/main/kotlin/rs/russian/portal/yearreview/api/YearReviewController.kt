package rs.russian.portal.yearreview.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rs.russian.portal.yearreview.service.YearReviewService
import java.util.UUID

@RestController
@RequestMapping
class YearReviewController(
    private val yearReviewService: YearReviewService,
) {
    @GetMapping("/me/year-review/{year}")
    fun myReview(@PathVariable year: Int): ResponseEntity<YearReviewDto> =
        ResponseEntity.ok(yearReviewService.myReview(year))

    @PostMapping("/me/year-review/{year}/certificate")
    fun issueCertificate(@PathVariable year: Int): ResponseEntity<AnnualCertificateDto> =
        ResponseEntity.ok(yearReviewService.issueMyCertificate(year))

    @PostMapping("/me/year-review/certificates/{id}/pdf")
    fun attachPdf(
        @PathVariable id: UUID,
        @RequestBody body: AttachCertificatePdfRequest,
    ): ResponseEntity<AnnualCertificateDto> =
        ResponseEntity.ok(yearReviewService.attachPdf(id, body))

    @GetMapping("/me/documents")
    fun myDocuments(): ResponseEntity<List<AnnualCertificateDto>> =
        ResponseEntity.ok(yearReviewService.myDocuments())

    @GetMapping("/verify/certificate/{token}")
    fun verify(@PathVariable token: String): ResponseEntity<AnnualCertificateVerifyDto> =
        ResponseEntity.ok(yearReviewService.verify(token))

    @GetMapping("/admin/year-review/{year}/criteria")
    fun getCriteria(@PathVariable year: Int): ResponseEntity<YearReviewCriteriaDto> =
        ResponseEntity.ok(yearReviewService.getCriteria(year))

    @PutMapping("/admin/year-review/{year}/criteria")
    fun updateCriteria(
        @PathVariable year: Int,
        @RequestBody body: YearReviewCriteriaUpdateRequest,
    ): ResponseEntity<YearReviewCriteriaDto> =
        ResponseEntity.ok(yearReviewService.updateCriteria(year, body))

    @PostMapping("/admin/year-review/{year}/certificate")
    fun adminIssue(
        @PathVariable year: Int,
        @RequestParam username: String,
    ): ResponseEntity<AnnualCertificateDto> =
        ResponseEntity.ok(yearReviewService.adminIssue(username, year))

    @PostMapping("/admin/year-review/certificates/{id}/revoke")
    fun adminRevoke(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<AnnualCertificateDto> =
        ResponseEntity.ok(yearReviewService.adminRevoke(id, reason))
}
