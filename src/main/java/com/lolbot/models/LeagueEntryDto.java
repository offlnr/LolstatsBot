package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single ranked queue entry from the League-V4 endpoint.
 *
 * Endpoint: GET /lol/league/v4/entries/by-puuid/{encryptedPUUID}
 * Host:     {platform}.api.riotgames.com
 *
 * Riot returns a JSON array. Each element corresponds to one queue:
 *  - "RANKED_SOLO_5x5"  ->  Solo/Duo queue
 *  - "RANKED_FLEX_SR"   ->  Flex 5v5 queue
 *
 * If the player has never played ranked, the array will be empty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueEntryDto {

    /** Queue type: "RANKED_SOLO_5x5" or "RANKED_FLEX_SR" */
    @JsonProperty("queueType")
    private String queueType;

    /** Tier: IRON, BRONZE, SILVER, GOLD, PLATINUM, EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER */
    @JsonProperty("tier")
    private String tier;

    /** Division: I, II, III, IV (empty for Master and above) */
    @JsonProperty("rank")
    private String rank;

    @JsonProperty("leaguePoints")
    private int leaguePoints;

    @JsonProperty("wins")
    private int wins;

    @JsonProperty("losses")
    private int losses;

    /** True if the player has won at least 3 consecutive games. */
    @JsonProperty("hotStreak")
    private boolean hotStreak;

    /** True if the player has played more than 100 games in this tier. */
    @JsonProperty("veteran")
    private boolean veteran;

    /** True if the player just promoted to this tier. */
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

    /** Calculates the win rate percentage rounded to one decimal place. */
    public double getWinRate() {
        int total = wins + losses;
        if (total == 0) return 0.0;
        return Math.round((wins * 100.0 / total) * 10.0) / 10.0;
    }

    /** Returns true if the tier has no divisions (Master, Grandmaster, Challenger). */
    public boolean isApexTier() {
        if (tier == null) return false;
        return switch (tier.toUpperCase()) {
            case "MASTER", "GRANDMASTER", "CHALLENGER" -> true;
            default -> false;
        };
    }

    /**
     * Returns the formatted rank string for display.
     * E.g. "GOLD II - 75 LP"  /  "MASTER - 1200 LP"
     */
    public String getFormattedRank() {
        if (isApexTier()) {
            return tier + " - " + leaguePoints + " LP";
        }
        return tier + " " + rank + " - " + leaguePoints + " LP";
    }
}
