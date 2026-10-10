package com.privatbank.cardsender;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "nova-poshta.enabled=false")
abstract class OracleIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        OracleTestDatabase.prepare();
        registry.add("spring.datasource.url", OracleTestDatabase::url);
        registry.add("spring.datasource.username", OracleTestDatabase::username);
        registry.add("spring.datasource.password", OracleTestDatabase::password);
    }
}
