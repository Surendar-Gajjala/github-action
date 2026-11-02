package com.gsr.onboarding.integration.openai;

import com.gsr.onboarding.exception.IntegrationException;

public interface OpenAIClient {

    /**
     * Generates onboarding content for the given employee facts.
     *
     * @throws IntegrationException when the API is unavailable or returns unusable output
     */
    GeneratedOnboardingContent generateOnboardingContent(OnboardingPromptInput input);
}
