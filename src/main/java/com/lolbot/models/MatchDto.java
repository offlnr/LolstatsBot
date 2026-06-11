package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchDto {

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("info")
    private Info info;

    public Metadata getMetadata() { return metadata; }
    public Info     getInfo()     { return info;     }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("matchId")
        private String matchId;

        public String getMatchId() { return matchId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        @JsonProperty("gameCreation")
        private long gameCreation;

        @JsonProperty("gameDuration")
        private long gameDuration;

        @JsonProperty("queueId")
        private int queueId;

        @JsonProperty("participants")
        private List<MatchParticipantDto> participants;

        public long                      getGameCreation()  { return gameCreation;  }
        public long                      getGameDuration()  { return gameDuration;  }
        public int                       getQueueId()       { return queueId;       }
        public List<MatchParticipantDto> getParticipants()  { return participants;  }
    }
}
