package com.transporter.assignment.exception;

import com.transporter.assignment.dto.ErrorResponse;
import com.transporter.assignment.solver.AssignmentSolver;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle Bean Validation errors → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorResponse("error", msg));
    }

    // AppException (includes NoInputDataException at 409)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ErrorResponse("error", ex.getMessage()));
    }

    // Infeasible → 422
    @ExceptionHandler(AssignmentSolver.InfeasibleException.class)
    public ResponseEntity<ErrorResponse> handleInfeasible(AssignmentSolver.InfeasibleException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse("error", ex.getMessage()));
    }

    // Solver limit exceeded → 400
    @ExceptionHandler(AssignmentSolver.SolverException.class)
    public ResponseEntity<ErrorResponse> handleSolver(AssignmentSolver.SolverException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("error", ex.getMessage()));
    }

    // Illegal argument → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("error", ex.getMessage()));
    }

    // Malformed JSON / Unreadable body → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse("error", "Malformed JSON request body: " + detail));
    }

    // Generic fallback → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("error", "Internal server error: " + ex.getMessage()));
    }
}
