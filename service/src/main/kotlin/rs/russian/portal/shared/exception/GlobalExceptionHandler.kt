package rs.russian.portal.shared.exception

import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import org.apache.catalina.connector.ClientAbortException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus.*
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import rs.russian.generated.model.ErrorResponse
import java.io.IOException

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error(ex.message, ex)
        return ResponseEntity(ErrorResponse(INTERNAL_SERVER_ERROR.reasonPhrase), INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(ex: NoSuchElementException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message ?: NOT_FOUND.reasonPhrase), NOT_FOUND)
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message ?: NOT_FOUND.reasonPhrase), NOT_FOUND)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(PAYLOAD_TOO_LARGE.reasonPhrase), PAYLOAD_TOO_LARGE)
    }

    @ExceptionHandler(UnsupportedFileFormat::class)
    fun handleUnsupportedFileFormat(ex: UnsupportedFileFormat, request: WebRequest): ResponseEntity<ErrorResponse> {
        val message = ex.message ?: UNSUPPORTED_MEDIA_TYPE.reasonPhrase
        log.warn("Unsupported file format on URL: {} - Error: {}", getRequestUrl(request), message)
        return ResponseEntity(ErrorResponse(message), UNSUPPORTED_MEDIA_TYPE)
    }

    @ExceptionHandler(TypeMismatchException::class)
    fun handleTypeMismatchException(
        ex: TypeMismatchException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(BAD_REQUEST.reasonPhrase), BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed on URL: {} - Errors: {}", getRequestUrl(request), errors)
        return ResponseEntity(ErrorResponse("Bad request: $errors"), BAD_REQUEST)
    }

    /**
     * Spring's own 400 mapping for these never applies: this advice does not extend
     * [org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler], so without an
     * explicit handler both fall through to [handleAllExceptions] and a malformed client request is reported
     * as a server fault. Only the client-error subtypes are listed — `MissingPathVariableException` is a
     * mapping bug on our side and must stay a 500.
     */
    @ExceptionHandler(MissingServletRequestParameterException::class, HttpMessageNotReadableException::class)
    fun handleMalformedRequest(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request on URL: {} - Error: {}", getRequestUrl(request), ex.message)
        // Surface Jackson/parse detail so external clients (Ekomapa sync) can diagnose 400s.
        val detail = ex.message?.trim()?.takeIf { it.isNotEmpty() } ?: BAD_REQUEST.reasonPhrase
        return ResponseEntity(ErrorResponse(detail.take(500)), BAD_REQUEST)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        val errors = ex.constraintViolations
            .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
        log.warn("Constraint violation on URL: {} - Errors: {}", getRequestUrl(request), errors)
        return ResponseEntity(ErrorResponse(BAD_REQUEST.reasonPhrase), BAD_REQUEST)
    }

    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequestException(
        ex: InvalidRequestException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message), BAD_REQUEST)
    }

    @ExceptionHandler(NotAuthorizedException::class)
    fun handleUnsupportedFileFormat(ex: NotAuthorizedException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(FORBIDDEN.reasonPhrase), FORBIDDEN)
    }

    @ExceptionHandler(CaptchaInvalidException::class)
    fun handleCaptchaInvalidException(ex: CaptchaInvalidException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(FORBIDDEN.reasonPhrase), FORBIDDEN)
    }

    @ExceptionHandler(ClientAbortException::class)
    fun handleClientAbortException(ex: ClientAbortException, request: WebRequest): ResponseEntity<ErrorResponse>? {
        val url = getRequestUrl(request)
        log.warn("Client abort (broken pipe) detected on URL: {} - Error: {}", url, ex.message)
        return null
    }

    @ExceptionHandler(AsyncRequestNotUsableException::class)
    fun handleAsyncRequestNotUsableException(
        ex: AsyncRequestNotUsableException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse>? {
        val url = getRequestUrl(request)
        val rootCause = ex.cause?.message ?: ex.message
        log.warn("Async request not usable (likely broken pipe) on URL: {} - Root cause: {}", url, rootCause)
        return null
    }

    @ExceptionHandler(IOException::class)
    fun handleIOException(ex: IOException, request: WebRequest): ResponseEntity<ErrorResponse>? {
        val message = ex.message?.lowercase() ?: ""
        if (message.contains("broken pipe") || message.contains("connection reset") || message.contains("client abort")) {
            val url = getRequestUrl(request)
            log.warn("Broken pipe/connection reset detected on URL: {} - Error: {}", url, ex.message)
            return null
        }
        log.error("IO Exception on URL: {} - Error: {}", getRequestUrl(request), ex.message, ex)
        return ResponseEntity(ErrorResponse(INTERNAL_SERVER_ERROR.reasonPhrase), INTERNAL_SERVER_ERROR)
    }

    private fun getRequestUrl(request: WebRequest): String {
        return when (request) {
            is ServletWebRequest -> {
                val httpRequest = request.request
                val url = StringBuilder()
                url.append(httpRequest.method).append(" ")
                url.append(httpRequest.requestURL)
                val queryString = httpRequest.queryString
                if (queryString != null) {
                    url.append("?").append(queryString)
                }
                url.toString()
            }

            else -> request.getDescription(true)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
