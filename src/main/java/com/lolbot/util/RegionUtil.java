package com.lolbot.util;

import java.util.Map;

/**
 * Utilities for resolving Riot Games API regions and clusters.
 *
 * The API uses two routing levels:
 *
 *  1. PLATFORMS (regional) — for Summoner-V4, League-V4, Champion-Mastery-V4
 *     Examples: na1, br1, la1, la2, euw1, eun1, tr1, ru, kr, jp1, oc1
 *
 *  2. CLUSTERS (continental) — for Account-V1, Match-V5
 *     Examples: americas, europe, asia, sea
 *
 * Always use the correct host for each endpoint.
 */
public final class RegionUtil {

    // Maps platform -> continental cluster for Account-V1 and Match-V5
    private static final Map<String, String> PLATFORM_TO_CLUSTER = Map.ofEntries(
        Map.entry("na1",  "americas"),
        Map.entry("na",   "americas"),
        Map.entry("br1",  "americas"),
        Map.entry("br",   "americas"),
        Map.entry("la1",  "americas"),
        Map.entry("la2",  "americas"),
        Map.entry("euw1", "europe"),
        Map.entry("euw",  "europe"),
        Map.entry("eun1", "europe"),
        Map.entry("eune", "europe"),
        Map.entry("tr1",  "europe"),
        Map.entry("tr",   "europe"),
        Map.entry("ru",   "europe"),
        Map.entry("kr",   "asia"),
        Map.entry("jp1",  "asia"),
        Map.entry("jp",   "asia"),
        Map.entry("oc1",  "sea"),
        Map.entry("oc",   "sea"),
        Map.entry("ph2",  "sea"),
        Map.entry("sg2",  "sea"),
        Map.entry("th2",  "sea"),
        Map.entry("tw2",  "sea"),
        Map.entry("vn2",  "sea")
    );

    // Normalizes common aliases to their official platform identifier
    private static final Map<String, String> ALIAS_TO_PLATFORM = Map.of(
        "na",   "na1",
        "br",   "br1",
        "lan",  "la1",
        "las",  "la2",
        "euw",  "euw1",
        "eune", "eun1",
        "tr",   "tr1",
        "jp",   "jp1",
        "oc",   "oc1"
    );

    private RegionUtil() {}

    /**
     * Returns the continental cluster for Account-V1 / Match-V5.
     * Falls back to "americas" if the region is unrecognized.
     *
     * E.g. "na1" -> "americas" | "euw1" -> "europe" | "kr" -> "asia"
     */
    public static String getCluster(String region) {
        return PLATFORM_TO_CLUSTER.getOrDefault(
            region.toLowerCase().trim(),
            "americas"
        );
    }

    /**
     * Normalizes common aliases to the official platform ID.
     * E.g. "na" -> "na1" | "EUW" -> "euw1" | "las" -> "la2"
     */
    public static String normalizePlatform(String region) {
        String normalized = region.toLowerCase().trim();
        return ALIAS_TO_PLATFORM.getOrDefault(normalized, normalized);
    }
}
