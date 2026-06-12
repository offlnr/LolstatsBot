package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps a single Clash tournament from the Clash-V1 response.
 * Endpoint: GET /lol/clash/v1/tournaments
 * Host: {platform}.api.riotgames.com
 *
 * The endpoint returns a JSON array; deserialize as ClashTournamentDto[].
 * An empty array means no tournaments are currently scheduled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClashTournamentDto {

    @JsonProperty("id")
    private int id;

    /** Internal key like "cup_name_demacia" — format it for display. */
    @JsonProperty("nameKey")
    private String nameKey;

    @JsonProperty("nameKeySecondary")
    private String nameKeySecondary;

    /** Each entry in the schedule represents one day/phase of the tournament. */
    @JsonProperty("schedule")
    private List<TournamentPhase> schedule;

    public int                   getId()               { return id;               }
    public String                getNameKey()          { return nameKey;          }
    public String                getNameKeySecondary() { return nameKeySecondary; }
    public List<TournamentPhase> getSchedule()        { return schedule;         }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TournamentPhase {

        @JsonProperty("id")
        private int id;

        /** Epoch milliseconds — when registration opens for this phase. */
        @JsonProperty("registrationTime")
        private long registrationTime;

        /** Epoch milliseconds — when matches for this phase begin. */
        @JsonProperty("startTime")
        private long startTime;

        @JsonProperty("cancelled")
        private boolean cancelled;

        public int     getId()               { return id;               }
        public long    getRegistrationTime() { return registrationTime; }
        public long    getStartTime()        { return startTime;        }
        public boolean isCancelled()         { return cancelled;        }
    }
}
