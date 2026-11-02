package com.example.plmdemo.common;

import java.util.List;

public record ErrorResponseDto(String code, String message, List<String> details, String correlationId) {
}
