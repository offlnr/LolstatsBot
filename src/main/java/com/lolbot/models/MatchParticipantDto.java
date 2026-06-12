package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchParticipantDto {

    @JsonProperty("puuid")
    private String puuid;

    @JsonProperty("championName")
    private String championName;

    @JsonProperty("kills")
    private int kills;

    @JsonProperty("deaths")
    private int deaths;

    @JsonProperty("assists")
    private int assists;

    @JsonProperty("win")
    private boolean win;

    @JsonProperty("totalMinionsKilled")
    private int totalMinionsKilled;

    @JsonProperty("neutralMinionsKilled")
    private int neutralMinionsKilled;

    @JsonProperty("champLevel")
    private int champLevel;

    @JsonProperty("visionWardsBoughtInGame")
    private int visionWardsBoughtInGame;

    @JsonProperty("visionScore")
    private int visionScore;

    @JsonProperty("totalDamageDealtToChampions")
    private int totalDamageDealtToChampions;

    public String  getPuuid()                        { return puuid;                               }
    public String  getChampionName()                 { return championName;                        }
    public int     getKills()                        { return kills;                               }
    public int     getDeaths()                       { return deaths;                              }
    public int     getAssists()                      { return assists;                             }
    public boolean isWin()                           { return win;                                 }
    public int     getChampLevel()                   { return champLevel;                          }
    public int     getTotalCS()                      { return totalMinionsKilled + neutralMinionsKilled; }
    public int     getVisionWardsBoughtInGame()      { return visionWardsBoughtInGame;             }
    public int     getVisionScore()                  { return visionScore;                         }
    public int     getTotalDamageDealtToChampions()  { return totalDamageDealtToChampions;         }

    public String getFormattedKda() {
        return kills + "/" + deaths + "/" + assists;
    }

    public String getKdaRatio() {
        if (deaths == 0) return "Perfecto";
        return String.format("%.1f", (double) (kills + assists) / deaths);
    }
}
