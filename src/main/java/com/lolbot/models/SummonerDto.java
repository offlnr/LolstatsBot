package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the Summoner-V4 endpoint.
 *
 * Endpoint: GET /lol/summoner/v4/summoners/by-puuid/{encryptedPUUID}
 * Host:     {platform}.api.riotgames.com  (e.g. na1.api.riotgames.com)
 *
 * Used to retrieve summoner level and profile icon for display purposes.
 * Note: the "id" field (encrypted summoner ID) may be null for accounts
 * migrated to the new Riot ID system — use PUUID-based endpoints instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummonerDto {

    @JsonProperty("id")
    private String summonerId;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("puuid")
    private String puuid;

    /** Legacy summoner name — may be empty for post-Riot ID accounts. */
    @JsonProperty("name")
    private String name;

    @JsonProperty("profileIconId")
    private int profileIconId;

    @JsonProperty("summonerLevel")
    private long summonerLevel;

    public String getSummonerId()    { return summonerId;    }
    public String getAccountId()     { return accountId;     }
    public String getPuuid()         { return puuid;         }
    public String getName()          { return name;          }
    public int    getProfileIconId() { return profileIconId; }
    public long   getSummonerLevel() { return summonerLevel; }
}
