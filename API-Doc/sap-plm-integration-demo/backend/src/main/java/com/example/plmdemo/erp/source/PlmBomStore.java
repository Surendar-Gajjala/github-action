package com.example.plmdemo.erp.source;

import com.example.plmdemo.erp.domain.EngineeringBom;

/** Writable PLM store: receives CAD imports and release state changes. */
public interface PlmBomStore extends PlmBomSource {

    EngineeringBom save(EngineeringBom bom);
}
