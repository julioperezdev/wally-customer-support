package com.wally.customersupport.conversation.infrastructure.ai.prompt;

/** Resolves an approved prompt without exposing its storage technology to the runtime. */
public interface PromptRegistry {

    PromptDefinition intentPrompt(String version);

    PromptDefinition responsePrompt(String version);
}
