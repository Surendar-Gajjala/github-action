package com.example.plmdemo.common;

/** Invalid request header or parameter (mapped to HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
