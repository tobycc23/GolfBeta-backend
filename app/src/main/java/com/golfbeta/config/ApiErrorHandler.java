package com.golfbeta.config;

import com.golfbeta.user.UsernameConflictException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(UsernameConflictException.class)
    public ResponseEntity<Map<String,Object>> handleUsername(UsernameConflictException e){
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error","username_conflict","message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleAny(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
    }
}
