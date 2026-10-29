package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({AuthSecurityProperties.class, CorsProperties.class, RateLimitProperties.class})
public class AuthInfrastructureConfig {

    /**
     * Bounded executor for audit writes. When the queue is full, events are dropped (and logged)
     * rather than blocking authentication requests.
     */
    @Bean(name = "authAuditExecutor")
    public ThreadPoolTaskExecutor authAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("auth-audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
