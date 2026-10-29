package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.AuthSecurityProperties;

import java.util.List;

/** Exposes test property fixtures to other packages. */
public final class ClientInfoResolverTestSupport {

    private ClientInfoResolverTestSupport() {
    }

    public static AuthSecurityProperties properties(List<String> trustedProxies) {
        return ClientInfoResolverTest.properties(trustedProxies);
    }
}
