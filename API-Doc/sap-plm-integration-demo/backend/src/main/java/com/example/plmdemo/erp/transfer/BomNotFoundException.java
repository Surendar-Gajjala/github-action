package com.example.plmdemo.erp.transfer;

public class BomNotFoundException extends RuntimeException {

    public BomNotFoundException(String materialNumber, String revision, String plant) {
        super("No BOM found in PLM for material " + materialNumber + " rev " + revision + " plant " + plant);
    }
}
