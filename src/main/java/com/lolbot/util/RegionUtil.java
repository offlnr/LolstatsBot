package com.lolbot.util;

import java.util.Map;

/**
 * Utilidades para resolver las regiones y clusters de la API de Riot Games.
 *
 * La API tiene DOS niveles de enrutamiento:
 *
 *  1. PLATAFORMAS (regionales) — para Summoner-V4, League-V4, Champion-Mastery-V4
 *     Ejemplos: na1, br1, la1, la2, euw1, eun1, tr1, ru, kr, jp1, oc1
 *
 *  2. CLUSTERS (continentales) — para Account-V1, Match-V5
 *     Ejemplos: americas, europe, asia, sea
 *
 * Siempre debes usar el host correcto para cada endpoint.
 */
public final class RegionUtil {

    // Mapa de plataforma → cluster para Account-V1 y Match-V5
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

    // Normaliza abreviaturas comunes al identificador oficial de plataforma
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

    private RegionUtil() { /* clase de utilidad, no instanciar */ }

    /**
     * Devuelve el cluster continental para Account-V1 / Match-V5.
     * Si la región no se reconoce, retorna "americas" como fallback seguro.
     *
     * Ej: "na1" → "americas" | "euw1" → "europe" | "kr" → "asia"
     */
    public static String getCluster(String region) {
        return PLATFORM_TO_CLUSTER.getOrDefault(
            region.toLowerCase().trim(),
            "americas"
        );
    }

    /**
     * Normaliza aliases comunes al ID oficial de plataforma.
     * Ej: "na" → "na1" | "EUW" → "euw1" | "na1" → "na1" (sin cambios)
     */
    public static String normalizePlatform(String region) {
        String normalized = region.toLowerCase().trim();
        return ALIAS_TO_PLATFORM.getOrDefault(normalized, normalized);
    }
}
