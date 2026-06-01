package com.microservice.people.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.microservice.people.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists( UserAlreadyExistsException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            409,
            "UserAlreadyExistsException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(409).body(error);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            404,
            "UserNotFoundException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(
            InvalidPasswordException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            400,
            "InvalidPasswordException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(
            AuthenticationFailedException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            401,
            "AuthenticationFailedException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(
            InvalidResetTokenException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            400,
            "InvalidResetTokenException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(
            UnauthorizedAccessException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            403,
            "UnauthorizedAccessException",
            ex.getMessage(),
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(403).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        ErrorResponse error = new ErrorResponse(
            400,
            "ValidationException",
            message,
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            403,
            "AccessDeniedException",
            "You do not have permission to access this resource",
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(403).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
            500,
            "InternalServerError",
            "An unexpected error occurred",
            LocalDateTime.now(),
            request.getRequestURI()
        );
        return ResponseEntity.status(500).body(error);
    }
}
