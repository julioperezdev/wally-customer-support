package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders only explicitly named placeholders; templates cannot execute code. */
public final class PromptTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)\\}\\}");

    private PromptTemplateRenderer() {
    }

    public static String render(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("user prompt template is empty");
        }
        String templateWithoutPlaceholders = PLACEHOLDER.matcher(template).replaceAll("");
        if (templateWithoutPlaceholders.contains("{{") || templateWithoutPlaceholders.contains("}}")) {
            throw new IllegalArgumentException("user prompt template contains a malformed placeholder");
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("unsupported user prompt placeholder: " + key);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(values.getOrDefault(key, "")));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
