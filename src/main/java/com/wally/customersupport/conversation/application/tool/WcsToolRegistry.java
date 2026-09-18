package com.wally.customersupport.conversation.application.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Registry for internal WCS capabilities.
 *
 * <p>The registry makes tool names explicit and rejects duplicate contracts at
 * startup. Provider adapters expose the descriptors and dispatch validated
 * arguments through this same boundary.</p>
 */
@Component
public class WcsToolRegistry {

    private final Map<String, WcsTool<?, ?>> tools;

    public WcsToolRegistry(List<WcsTool<?, ?>> tools) {
        Objects.requireNonNull(tools, "tools");
        Map<String, WcsTool<?, ?>> indexed = new LinkedHashMap<>();
        for (WcsTool<?, ?> tool : tools) {
            Objects.requireNonNull(tool, "tool");
            String name = tool.descriptor().name();
            if (indexed.putIfAbsent(name, tool) != null) {
                throw new IllegalArgumentException("Duplicate WCS tool: " + name);
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public List<WcsToolDescriptor> descriptors() {
        return tools.values().stream().map(WcsTool::descriptor).toList();
    }

    public <T extends WcsTool<?, ?>> Optional<T> find(String name, Class<T> expectedType) {
        WcsTool<?, ?> tool = tools.get(name);
        if (tool == null || !expectedType.isInstance(tool)) {
            return Optional.empty();
        }
        return Optional.of(expectedType.cast(tool));
    }
}
