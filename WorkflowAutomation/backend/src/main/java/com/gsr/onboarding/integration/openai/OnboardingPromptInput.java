package com.gsr.onboarding.integration.openai;

import java.time.LocalDate;

import com.gsr.onboarding.domain.entity.Employee;

/**
 * The only employee facts sent to the language model. Email address, last name, GitHub username
 * and internal identifiers are deliberately excluded (data minimisation).
 */
public record OnboardingPromptInput(
        String firstName,
        String jobTitle,
        String department,
        LocalDate joiningDate,
        String accessLevel,
        String companyName) {

    public static OnboardingPromptInput from(Employee employee, String companyName) {
        return new OnboardingPromptInput(
                employee.getFirstName(),
                employee.getJobTitle(),
                employee.getDepartment(),
                employee.getJoiningDate(),
                employee.getAccessLevel().name(),
                companyName);
    }
}
