package com.example.empiktask;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.empiktask.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class EmpikTaskApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsWithAllMigrationsApplied() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(appliedMigrations).isEqualTo(3);
    }
}
