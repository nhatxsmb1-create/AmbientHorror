package dev.ambienthorror.events;

public record AmbientEventData(
        String key,
        String soundKey,
        double minPresence,
        double minScore,
        int weight,
        int cooldownSeconds,
        boolean enabled
) {}
