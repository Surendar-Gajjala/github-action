package com.example.plmdemo.erp.domain;

/** Release state owned by the source PLM system. Only RELEASED BOMs may be transferred. */
public enum ReleaseState {
    IN_WORK,
    RELEASED,
    OBSOLETE
}
