package com.aeroroster.verification;

import com.aeroroster.persistence.entity.Flight;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.support.PostgresTest;
import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
class FreshMigrationVerificationTest {
    @Autowired DataSource dataSource;
    private static final Map<String, Integer> SEEDS = Map.of("airport", 5, "aircraft_type", 2,
            "aircraft", 3, "crew", 12, "qualification", 6, "crew_qualification", 12,
            "flight", 5, "crew_unavailability", 1, "duty_assignment", 4, "flight_audit", 5);

    @Test
    void emptyIsolatedSchemaMigratesValidatesMapsAndAuditsWithoutTouchingPublicData() throws Exception {
        String schema = "phase25_verify_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(schema.matches("phase25_verify_[a-f0-9]{32}"));
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("aeroroster_test", connection.getCatalog());
            String original = connection.getSchema();
            boolean autoCommit = connection.getAutoCommit();
            SingleConnectionDataSource scoped = new SingleConnectionDataSource(connection, true);
            JdbcTemplate sql = new JdbcTemplate(scoped);
            Map<String, Long> publicCounts = new HashMap<>();
            SEEDS.keySet().forEach(table -> publicCounts.put(table,
                    sql.queryForObject("select count(*) from public." + table, Long.class)));
            sql.execute("create schema " + schema);
            try {
                connection.setSchema(schema);
                Flyway flyway = Flyway.configure().dataSource(scoped).defaultSchema(schema).schemas(schema)
                        .createSchemas(false).load();
                assertEquals(3, flyway.migrate().migrationsExecuted);
                flyway.validate();
                connection.setSchema(schema);
                assertEquals(List.of("1", "2", "3"), sql.queryForList(
                        "select version from flyway_schema_history where success and version is not null order by installed_rank", String.class));
                SEEDS.forEach((table, count) -> assertEquals(count,
                        sql.queryForObject("select count(*) from " + table, Integer.class), table));
                for (String table : List.of("crew", "aircraft", "flight", "crew_qualification", "crew_unavailability", "duty_assignment")) {
                    assertEquals(0, sql.queryForObject("select count(*) from " + table + " where version<>0", Integer.class), table);
                }
                assertEquals(10, sql.queryForObject("select count(*) from information_schema.table_constraints where constraint_schema=? and constraint_type='FOREIGN KEY'", Integer.class, schema));
                assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                        () -> sql.update("insert into crew_qualification(crew_id,qualification_id) values (?,?)", Long.MAX_VALUE, Long.MAX_VALUE));
                LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
                factory.setDataSource(scoped);
                factory.setPackagesToScan("com.aeroroster.persistence.entity");
                factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.default_schema", schema));
                try {
                    factory.afterPropertiesSet();
                    var emf = Objects.requireNonNull(factory.getObject());
                    assertEquals(10, emf.getMetamodel().getEntities().size());
                    var em = emf.createEntityManager();
                    try {
                        em.getTransaction().begin();
                        Long id = sql.queryForObject("select min(id) from flight", Long.class);
                        Flight flight = em.find(Flight.class, id);
                        assertEquals(FlightStatus.SCHEDULED, flight.getStatus());
                        assertNotNull(flight.getDepartureTime());
                        assertNotNull(flight.getAircraft().getAircraftType().getModel());
                        assertNotEquals(flight.getSourceAirport().getId(), flight.getDestinationAirport().getId());
                        flight.setFlightNumber("V25-AUDIT");
                        em.flush();
                        assertEquals(1, sql.queryForObject("select count(*) from flight_audit where flight_id=? and action='UPDATE'", Integer.class, id));
                    } finally {
                        if (em.getTransaction().isActive()) em.getTransaction().rollback();
                        em.close();
                    }
                    assertEquals(5, sql.queryForObject("select count(*) from flight_audit", Integer.class));
                } finally {
                    factory.destroy();
                }
            } finally {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(true);
                connection.setSchema(original);
                // Only the unique schema successfully created above belongs to this test.
                sql.execute("drop schema " + schema + " cascade");
                connection.setAutoCommit(autoCommit);
            }
            publicCounts.forEach((table, count) -> assertEquals(count,
                    sql.queryForObject("select count(*) from public." + table, Long.class), table));
        }
    }
}
