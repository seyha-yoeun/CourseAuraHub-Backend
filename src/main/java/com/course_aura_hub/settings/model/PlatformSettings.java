package com.course_aura_hub.settings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "platform_name", nullable = false, columnDefinition = "text")
    private String platformName;

    @Column(name = "platform_tagline", nullable = false, columnDefinition = "text")
    private String platformTagline;

    @Column(name = "currency", nullable = false, columnDefinition = "text")
    private String currency;

    @Column(name = "default_language", nullable = false, columnDefinition = "text")
    private String defaultLanguage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notifications", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Boolean> notifications = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> profile = new LinkedHashMap<>();
}
