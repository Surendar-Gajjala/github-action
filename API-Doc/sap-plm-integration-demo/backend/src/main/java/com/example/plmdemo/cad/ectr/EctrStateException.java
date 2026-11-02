package com.example.plmdemo.cad.ectr;

/** A step was called in the wrong order or twice (mapped to HTTP 409). */
public class EctrStateException extends RuntimeException {

    public EctrStateException(String message) {
        super(message);
    }
}
