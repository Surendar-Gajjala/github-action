package com.gsr.onboarding.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gsr.onboarding.domain.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdempotencyKey(String idempotencyKey);

    boolean existsByEmail(String email);

    @Query("""
            select e from Employee e
            where lower(e.firstName) like :search
               or lower(e.lastName) like :search
               or lower(concat(e.firstName, ' ', e.lastName)) like :search
               or lower(e.email) like :search
               or lower(e.employeeNumber) like :search
               or lower(e.department) like :search
            """)
    Page<Employee> search(@Param("search") String search, Pageable pageable);

    @Query(value = "select nextval('employee_number_seq')", nativeQuery = true)
    long nextEmployeeNumber();
}
