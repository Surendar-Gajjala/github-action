package com.example.plmdemo.erp.sap;

/** Result returned by a SapBomGateway. In mock mode targetReference starts with "MOCK-". */
public record SapBomResponse(String targetReference, String message) {
}
