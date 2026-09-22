package com.wally.customersupport.agent.domain.model;

import java.util.Objects;

/** Strict MAJOR.MINOR.PATCH ordering used independently of the SQL surrogate version. */
public record AgentSemanticVersion(int major, int minor, int patch) implements Comparable<AgentSemanticVersion> {

    public AgentSemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("semantic version components must not be negative");
        }
    }

    public static AgentSemanticVersion parse(String value) {
        String candidate = Objects.requireNonNull(value, "semanticVersion").trim();
        if (!candidate.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("semanticVersion must use MAJOR.MINOR.PATCH without leading zeroes");
        }
        String[] parts = candidate.split("\\.");
        try {
            return new AgentSemanticVersion(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("semanticVersion component is too large", exception);
        }
    }

    public AgentSemanticVersion nextPatch() {
        return new AgentSemanticVersion(major, minor, Math.incrementExact(patch));
    }

    @Override
    public int compareTo(AgentSemanticVersion other) {
        int majorResult = Integer.compare(major, other.major);
        if (majorResult != 0) return majorResult;
        int minorResult = Integer.compare(minor, other.minor);
        return minorResult != 0 ? minorResult : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
