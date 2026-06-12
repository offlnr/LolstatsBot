package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stores the Riot account linked to a Discord user.
 * Persisted as a JSON entry in data/user-links.json, keyed by Discord user ID.
 */
public class UserLinkDto {

    private final String riotId;   // full Riot ID: "Name#TAG"
    private final String region;   // normalized platform: "na1", "euw1", etc.

    @JsonCreator
    public UserLinkDto(
            @JsonProperty("riotId")  String riotId,
            @JsonProperty("region")  String region) {
        this.riotId  = riotId;
        this.region  = region;
    }

    public String getRiotId() { return riotId; }
    public String getRegion() { return region; }
}
