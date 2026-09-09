package com.wally.customersupport.featureflag.application;

import java.util.List;

/** One allow-listed business behavior. Empty dimensions mean all values. */
public record FeatureFlagDefinition(
        String key,
        boolean enabled,
        boolean killSwitch,
        List<String> environments,
        List<String> channels,
        List<String> useCases,
        List<String> agentIds,
        List<Integer> agentVersions) {

    public FeatureFlagDefinition {
        key = key == null ? null : key.strip();
        environments = immutableStrings(environments);
        channels = immutableStrings(channels);
        useCases = immutableStrings(useCases);
        agentIds = immutableStrings(agentIds);
        agentVersions = agentVersions == null ? List.of() : List.copyOf(agentVersions);
    }

    public boolean matches(FeatureFlagContext context) {
        return matches(environments, context.environment())
                && matches(channels, context.channel())
                && matches(useCases, context.useCase())
                && matches(agentIds, context.agentId())
                && (agentVersions.isEmpty()
                        || (context.agentVersion() != null && agentVersions.contains(context.agentVersion())));
    }

    private static boolean matches(List<String> allowed, String actual) {
        return allowed.isEmpty() || (actual != null && allowed.contains(actual));
    }

    private static List<String> immutableStrings(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
