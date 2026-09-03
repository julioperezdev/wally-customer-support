package com.wally.customersupport.adapter.out.ai.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MockLlmClientTest {

    @Test
    void returnsTheDevelopmentReplyWithoutExternalConfiguration() {
        MockLlmClient client = new MockLlmClient();

        assertEquals(
                "Gracias por escribirnos. Un agente revisará tu consulta.",
                client.generateReply(null));
    }
}
