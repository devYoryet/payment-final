package com.zosh.exception;

import com.zosh.payload.response.ExceptionResponse;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UserException.class)
	public ResponseEntity<ExceptionResponse> handleUserException(
			UserException ex, WebRequest req) {
		ExceptionResponse response = new ExceptionResponse(
				ex.getMessage(),
				req.getDescription(false),
				LocalDateTime.now());
		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(ReviewException.class)
	public ResponseEntity<ExceptionResponse> ReviewExistExceptionHandler(
			ReviewException ex, WebRequest req) {
		ExceptionResponse response = new ExceptionResponse(
				ex.getMessage(),
				req.getDescription(false), LocalDateTime.now());
		return ResponseEntity.ok(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ExceptionResponse> ExceptionHandler(Exception ex, WebRequest req) {
		ExceptionResponse response = new ExceptionResponse(
				ex.getMessage(),
				req.getDescription(false), LocalDateTime.now());
		// response.setMessage(ex.getMessage());
		return ResponseEntity.ok(response);
	}

}
