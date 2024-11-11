package com.bytefish.bytecore.api.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiResponse {

	private final boolean success;
	private final String message;
	private final Map<String, Object> data;

	private ApiResponse(
		boolean success,
		String message,
		Map<String, Object> data
	) {
		this.success = success;
		this.message = message;
		this.data = data != null ? new HashMap<>(data) : new HashMap<>();
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public Map<String, Object> getData() {
		return new HashMap<>(data);
	}

	public static ApiResponse success(String message) {
		return new ApiResponse(true, message, null);
	}

	public static ApiResponse success(
		String message,
		Map<String, Object> data
	) {
		return new ApiResponse(true, message, data);
	}

	public static ApiResponse error(String message) {
		return new ApiResponse(false, message, null);
	}

	public static ApiResponse error(String message, Map<String, Object> data) {
		return new ApiResponse(false, message, data);
	}
}
