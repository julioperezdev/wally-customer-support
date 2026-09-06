package com.wally.customersupport.conversation.infrastructure.memory.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ConversationMemoryPolicy;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import org.junit.jupiter.api.Test;

class InMemoryConversationMemoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-06T03:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void isolatesStateByConversationAndActor() {
        var memory = new InMemoryConversationMemoryAdapter(ConversationMemoryPolicy.recommended(), CLOCK);
        var state = new ConversationState(CONVERSATION_ID, "actor-a", List.of("buzo", "negro"), NOW);

        memory.save(state);

        assertEquals(state, memory.load(CONVERSATION_ID, "actor-a").orElseThrow());
        assertTrue(memory.load(CONVERSATION_ID, "actor-b").isEmpty());
        assertTrue(memory.load(OTHER_CONVERSATION_ID, "actor-a").isEmpty());
    }

    @Test
    void expiresAndRemovesStateAtTheRetentionBoundary() {
        var clock = new MutableClock(NOW);
        var memory = new InMemoryConversationMemoryAdapter(ConversationMemoryPolicy.recommended(), clock);
        memory.save(new ConversationState(CONVERSATION_ID, "actor-a", List.of("consulta"), NOW));

        clock.advance(Duration.ofHours(24));

        assertTrue(memory.load(CONVERSATION_ID, "actor-a").isEmpty());
        assertTrue(memory.load(CONVERSATION_ID, "actor-a").isEmpty());
    }

    @Test
    void clearsStateWithoutAffectingAnotherActor() {
        var memory = new InMemoryConversationMemoryAdapter(ConversationMemoryPolicy.recommended(), CLOCK);
        memory.save(new ConversationState(CONVERSATION_ID, "actor-a", List.of("consulta"), NOW));
        memory.save(new ConversationState(CONVERSATION_ID, "actor-b", List.of("otra consulta"), NOW));

        memory.clear(CONVERSATION_ID, "actor-a");

        assertTrue(memory.load(CONVERSATION_ID, "actor-a").isEmpty());
        assertFalse(memory.load(CONVERSATION_ID, "actor-b").isEmpty());
    }

    @Test
    void appliesMessageAndWindowLimitsKeepingTheMostRecentMessages() {
        var policy = new ConversationMemoryPolicy(Duration.ofHours(1), 2, 5);
        var memory = new InMemoryConversationMemoryAdapter(policy, CLOCK);

        var saved = memory.save(new ConversationState(
                CONVERSATION_ID,
                "actor-a",
                List.of(" uno ", "", "dos largos", "tres"),
                NOW));

        assertEquals(List.of("dos l", "tres"), saved.recentMessages());
        assertEquals(saved, memory.load(CONVERSATION_ID, "actor-a").orElseThrow());
    }

    @Test
    void rejectsSavingAlreadyExpiredState() {
        var clock = Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        var memory = new InMemoryConversationMemoryAdapter(
                new ConversationMemoryPolicy(Duration.ofHours(1), 20, 2_000), clock);

        assertThrows(IllegalArgumentException.class, () -> memory.save(
                new ConversationState(CONVERSATION_ID, "actor-a", List.of("consulta"), NOW)));
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
