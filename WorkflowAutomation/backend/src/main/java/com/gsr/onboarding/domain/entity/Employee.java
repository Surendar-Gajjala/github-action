package com.gsr.onboarding.domain.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.gsr.onboarding.domain.enums.AccessLevel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employee extends BaseEntity {

    @Column(name = "employee_number", nullable = false, updatable = false, length = 20)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "reporting_manager", nullable = false, length = 150)
    private String reportingManager;

    @Column(name = "github_username", nullable = false, length = 39)
    private String githubUsername;

    @Column(name = "github_repository", nullable = false, length = 200)
    private String githubRepository;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel;

    @Column(name = "idempotency_key", updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
