package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Una entrada de clasificación dentro de la respuesta de League-V4.
 *
 * Endpoint: GET /lol/league/v4/entries/by-summoner/{encryptedSummonerId}
 * Host:     {region}.api.riotgames.com
 *
 * Riot devuelve un ARRAY JSON. Cada elemento corresponde a una cola:
 *  - "RANKED_SOLO_5x5"  →  Cola Solo/Duo
 *  - "RANKED_FLEX_SR"   →  Cola Flex 5v5
 *
 * Si el jugador nunca ha jugado clasificatoria, el array estará vacío.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueEntryDto {

    /** Tipo de cola: "RANKED_SOLO_5x5" o "RANKED_FLEX_SR" */
    @JsonProperty("queueType")
    private String queueType;

    /** Tier: IRON, BRONZE, SILVER, GOLD, PLATINUM, EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER */
    @JsonProperty("tier")
    private String tier;

    /** División: I, II, III, IV (vacío en Master+) */
    @JsonProperty("rank")
    private String rank;

    @JsonProperty("leaguePoints")
    private int leaguePoints;

    @JsonProperty("wins")
    private int wins;

    @JsonProperty("losses")
    private int losses;

    /** El jugador ha ganado al menos 3 partidas consecutivas */
    @JsonProperty("hotStreak")
    private boolean hotStreak;

    /** El jugador lleva más de 100 partidas en este tier */
    @JsonProperty("veteran")
    private boolean veteran;

    /** El jugador acaba de ascender a este tier */
    @JsonProperty("freshBlood")
    private boolean freshBlood;

    // -------------------------------------------------------------------------

    public String  getQueueType()    { return queueType;    }
    public String  getTier()         { return tier;         }
    public String  getRank()         { return rank;         }
    public int     getLeaguePoints() { return leaguePoints; }
    public int     getWins()         { return wins;         }
    public int     getLosses()       { return losses;       }
    public boolean isHotStreak()     { return hotStreak;    }
    public boolean isVeteran()       { return veteran;      }
    public boolean isFreshBlood()    { return freshBlood;   }

    /** Calcula el porcentaje de victorias con un decimal. */
    public double getWinRate() {
        int total = wins + losses;
        if (total == 0) return 0.0;
        return Math.round((wins * 100.0 / total) * 10.0) / 10.0;
    }

    /** Indica si es un tier donde no hay divisiones (Master, Grandmaster, Challenger) */
    public boolean isApexTier() {
        if (tier == null) return false;
        return switch (tier.toUpperCase()) {
            case "MASTER", "GRANDMASTER", "CHALLENGER" -> true;
            default -> false;
        };
    }

    /**
     * Devuelve el rango formateado para mostrar.
     * Ej: "GOLD II — 75 LP"  /  "MASTER — 1200 LP"
     */
    public String getFormattedRank() {
        if (isApexTier()) {
            return tier + " — " + leaguePoints + " LP";
        }
        return tier + " " + rank + " — " + leaguePoints + " LP";
    }
}
