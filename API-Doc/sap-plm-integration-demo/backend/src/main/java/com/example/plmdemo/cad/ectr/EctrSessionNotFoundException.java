package com.example.plmdemo.cad.ectr;

public class EctrSessionNotFoundException extends RuntimeException {

    public EctrSessionNotFoundException(String sessionId) {
        super("ECTR session " + sessionId + " not found (sessions are kept in memory and lost on restart)");
    }
}
