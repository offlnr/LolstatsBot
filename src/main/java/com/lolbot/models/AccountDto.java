package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the Riot Account-V1 endpoint.
 *
 * Endpoint: GET /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}
 * Host:     {cluster}.api.riotgames.com  (e.g. americas, europe, asia, sea)
 *
 * The PUUID is the player's global unique identifier, independent of region.
 * It is the starting point for all subsequent API calls.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountDto {

    @JsonProperty("puuid")
    private String puuid;

    @JsonProperty("gameName")
    private String gameName;

    @JsonProperty("tagLine")
    private String tagLine;

    public String getPuuid()    { return puuid;    }
    public String getGameName() { return gameName; }
    public String getTagLine()  { return tagLine;  }

    /** Returns the full Riot ID in "Name#TAG" format. */
    public String getFullRiotId() {
        return gameName + "#" + tagLine;
    }
}
