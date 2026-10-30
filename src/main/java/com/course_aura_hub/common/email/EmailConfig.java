package com.course_aura_hub.common.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(AppMailProperties.class)
public class EmailConfig {

    @Bean
    public EmailSender emailSender(AppMailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        JavaMailSender javaMailSender = mailSender.getIfAvailable();
        if (!properties.enabled() || javaMailSender == null) {
            return new DisabledEmailSender();
        }
        return new SmtpEmailSender(javaMailSender, properties);
    }
}
