package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps the Spectator-V5 active game response.
 * Endpoint: GET /lol/spectator/v5/active-games/by-summoner/{encryptedPUUID}
 * Host: {platform}.api.riotgames.com
 *
 * Returns 404 when the player is not currently in a game — handle that case
 * in the calling service method rather than treating it as an error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiveGameDto {

    @JsonProperty("gameId")
    private long gameId;

    /** Same queue IDs used by Match-V5: 420=SoloQ, 440=Flex, 450=ARAM, etc. */
    @JsonProperty("gameQueueConfigId")
    private int gameQueueConfigId;

    /** Epoch milliseconds when the game lobby/loading screen began. 0 during loading. */
    @JsonProperty("gameStartTime")
    private long gameStartTime;

    /** Seconds elapsed since the game started. 0 during the loading screen. */
    @JsonProperty("gameLength")
    private long gameLength;

    @JsonProperty("gameMode")
    private String gameMode;

    @JsonProperty("participants")
    private List<Participant> participants;

    public long              getGameId()           { return gameId;           }
    public int               getGameQueueConfigId() { return gameQueueConfigId; }
    public long              getGameStartTime()    { return gameStartTime;    }
    public long              getGameLength()       { return gameLength;       }
    public String            getGameMode()         { return gameMode;         }
    public List<Participant> getParticipants()     { return participants;     }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Participant {

        /** Available in Spectator-V5; lets us identify the target player without summonerId. */
        @JsonProperty("puuid")
        private String puuid;

        @JsonProperty("summonerName")
        private String summonerName;

        @JsonProperty("championId")
        private int championId;

        /** 100 = Blue side, 200 = Red side. */
        @JsonProperty("teamId")
        private int teamId;

        public String getPuuid()        { return puuid;        }
        public String getSummonerName() { return summonerName; }
        public int    getChampionId()   { return championId;   }
        public int    getTeamId()       { return teamId;       }
    }
}
