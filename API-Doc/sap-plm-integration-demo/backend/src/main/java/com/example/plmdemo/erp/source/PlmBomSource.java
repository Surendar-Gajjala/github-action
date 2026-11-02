package com.example.plmdemo.erp.source;

import com.example.plmdemo.erp.domain.EngineeringBom;

import java.util.Optional;

/** Reads engineering BOMs from the source PLM system (the system of record for engineering data). */
public interface PlmBomSource {

    Optional<EngineeringBom> findBom(String materialNumber, String revision, String plant);
}
