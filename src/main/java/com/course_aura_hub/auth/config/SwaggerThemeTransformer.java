package com.course_aura_hub.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Gives Swagger UI a dark theme and a light/dark switch. springdoc already rewrites {@code swagger-initializer.js}
 * on every page load; this appends a few lines to it that pick the saved or system theme before the page draws,
 * then load {@code static/swagger-theme/} (the dark styles and the switch). The page itself stays springdoc's.
 */
class SwaggerThemeTransformer extends SwaggerIndexPageTransformer {

    private static final String INITIALIZER = "swagger-initializer.js";

    // Relative to /swagger-ui/index.html, so it also works under a servlet context path
    static final String THEME_LOADER = """

            // Course Aura Hub: dark mode for these docs (SwaggerThemeTransformer). Pick the saved or system theme
            // before anything draws, then load the dark styles and the light/dark switch.
            (function () {
              var theme = null;
              try { theme = localStorage.getItem('swagger-theme'); } catch (e) {}
              if (theme !== 'dark' && theme !== 'light') {
                theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
              }
              document.documentElement.setAttribute('data-theme', theme);
              var styles = document.createElement('link');
              styles.rel = 'stylesheet';
              styles.href = '../swagger-theme/swagger-dark.css';
              document.head.appendChild(styles);
              var toggle = document.createElement('script');
              toggle.src = '../swagger-theme/swagger-theme.js';
              document.body.appendChild(toggle);
            })();
            """;

    SwaggerThemeTransformer(SwaggerUiConfigProperties swaggerUiConfig, SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                            SwaggerWelcomeCommon swaggerWelcomeCommon, ObjectMapperProvider objectMapperProvider) {
        super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain transformerChain)
            throws IOException {
        Resource transformed = super.transform(request, resource, transformerChain);
        if (!INITIALIZER.equals(resource.getFilename())) {
            return transformed;
        }
        String script;
        try (InputStream in = transformed.getInputStream()) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new TransformedResource(resource, (script + THEME_LOADER).getBytes(StandardCharsets.UTF_8));
    }
}
