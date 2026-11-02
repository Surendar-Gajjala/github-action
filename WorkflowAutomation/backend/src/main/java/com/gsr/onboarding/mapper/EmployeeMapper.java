package com.gsr.onboarding.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.gsr.onboarding.domain.entity.Employee;
import com.gsr.onboarding.dto.EmployeeRequest;
import com.gsr.onboarding.dto.EmployeeResponses.EmployeeResponse;

@Mapper
public interface EmployeeMapper {

    @Mapping(target = "email", source = "workEmail")
    Employee toEntity(EmployeeRequest request);

    EmployeeResponse toResponse(Employee employee);
}
