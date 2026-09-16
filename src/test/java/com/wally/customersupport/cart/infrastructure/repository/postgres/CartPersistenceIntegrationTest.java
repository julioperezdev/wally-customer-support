package com.wally.customersupport.cart.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.cart.domain.model.CartStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CartPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_cart_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void appliesCartSchemaAndLinksItToOrders() {
        assertThat(jdbcTemplate.queryForObject("select to_regclass('wcs.carts')", String.class))
                .isEqualTo("carts");
        assertThat(jdbcTemplate.queryForObject("select to_regclass('wcs.cart_items')", String.class))
                .isEqualTo("cart_items");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'wcs' and table_name = 'orders' "
                + "and column_name in ('cart_id', 'cart_version')", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void persistsCartItemsAndReloadsTheAggregateWithItsBusinessVersion() {
        Instant now = Instant.parse("2026-09-16T20:00:00Z");
        String suffix = UUID.randomUUID().toString();
        var conversation = conversationRepository.findOrCreate(
                Channel.TELEGRAM, "cart-conversation-" + suffix, "customer-" + suffix, now);
        CartJpaEntity cart = new CartJpaEntity(
                UUID.randomUUID(), conversation.id(), "actor-hash", Channel.TELEGRAM, "ARS", now);
        cart.addOrIncrement("RP-REM-NP-NEG-M", 2, now);

        cartRepository.saveAndFlush(cart);
        CartJpaEntity reloaded = cartRepository.findByConversationId(conversation.id()).orElseThrow();

        assertThat(reloaded.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getSku()).isEqualTo("RP-REM-NP-NEG-M");
                    assertThat(item.getQuantity()).isEqualTo(2);
                });
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(CartStatus.ACTIVE);
    }
}
