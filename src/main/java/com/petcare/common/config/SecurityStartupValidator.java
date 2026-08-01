package com.petcare.common.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Rejects unsafe production startup when required security configuration is missing.
 */
@Component
public class SecurityStartupValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    public SecurityStartupValidator() {
    }

    SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate();
    }

    void validate() {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !StringUtils.hasText(environment.getProperty("petcare.security.jwt-secret"))) {
            throw new IllegalStateException("JWT_SECRET must be configured for the prod profile");
        }
    }
}
