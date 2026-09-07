package com.wally.customersupport.conversation.infrastructure.memory.noop;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "wcs.conversation.memory.enabled=false")
@Testcontainers
class NoOpConversationMemoryAdapterContextTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConversationMemory conversationMemory;

    @Test
    void registersNoOpAdapterWhenConversationMemoryIsDisabled() {
        assertInstanceOf(NoOpConversationMemoryAdapter.class, conversationMemory);
    }
}
