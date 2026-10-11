package com.privatbank.cardsender.advisors;

import com.privatbank.cardsender.domain.BusinessException;
import com.privatbank.cardsender.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> businessExceptionHandler(BusinessException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message;
        if (exception instanceof MethodArgumentNotValidException validation) {
            message = validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .sorted().findFirst().orElse("Invalid request");
        } else if (exception instanceof HttpMessageNotReadableException) {
            message = "Malformed JSON or invalid request value";
        } else if (exception instanceof MissingRequestHeaderException missing) {
            message = "Missing required header: " + missing.getHeaderName();
        } else if (exception instanceof MissingServletRequestParameterException missing) {
            message = "Missing required parameter: " + missing.getParameterName();
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Invalid value for parameter: " + mismatch.getName();
        } else {
            HttpStatus knownStatus = HttpStatus.resolve(status.value());
            message = knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase();
        }
        return super.handleExceptionInternal(exception, new ErrorResponse(message), headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpectedExceptionHandler(Exception exception) {
        logger.error("Unhandled API failure (" + exception.getClass().getName() + ")");
        return ResponseEntity.internalServerError().body(new ErrorResponse("Internal server error"));
    }
}
