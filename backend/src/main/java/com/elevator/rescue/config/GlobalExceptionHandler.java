package com.elevator.rescue.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public class GlobalExceptionHandler {

    public record ApiError(String message) {
    }

    @RestControllerAdvice
    public static class Handler {

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("用户名或密码错误"));
        }

        @ExceptionHandler(DisabledException.class)
        public ResponseEntity<ApiError> disabled(DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("账号已被停用，请联系管理员"));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiError> accessDenied(AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("无权限执行该操作"));
        }

        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
        public ResponseEntity<ApiError> badRequest(RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
            FieldError fieldError = e.getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
            return ResponseEntity.badRequest().body(new ApiError(message));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiError> notReadable(HttpMessageNotReadableException e) {
            return ResponseEntity.badRequest().body(new ApiError("请求参数格式错误，请检查输入"));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiError> serverError(Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("服务器内部错误: " + e.getMessage()));
        }
    }
}
