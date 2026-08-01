package com.petcare.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityStartupValidatorTest {

    @Test
    void run_shouldRejectBlankJwtSecretInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("petcare.security.jwt-secret", "");
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void run_shouldAllowBlankJwtSecretOutsideProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("petcare.security.jwt-secret", "");
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void run_shouldAllowConfiguredJwtSecretInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("petcare.security.jwt-secret", "replace-with-production-secret");
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);

        assertDoesNotThrow(validator::validate);
    }
}
