package com.course_aura_hub.config;

import com.course_aura_hub.auth.AuthIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void seed_isRecordedSoFlywayNeverRunsItAgain() {
        Integer runs = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '0036' and success",
                Integer.class);

        assertThat(runs).isEqualTo(1);
    }

    @Test
    void seed_addsCouponsAndOrderHistory() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from coupons where code = 'WELCOME10'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where order_number like 'SEED-%'", Integer.class))
                .isPositive();
    }
}
