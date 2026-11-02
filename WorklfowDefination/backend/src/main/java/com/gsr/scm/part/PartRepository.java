package com.gsr.scm.part;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, Long> {

    boolean existsByPartNumberIgnoreCase(String partNumber);

    @Query("""
            select p from Part p
            where (:q is null or lower(p.partNumber) like :q or lower(p.name) like :q)
              and (:partType is null or p.partType = :partType)
            """)
    Page<Part> search(@Param("q") String q, @Param("partType") String partType, Pageable pageable);
}
