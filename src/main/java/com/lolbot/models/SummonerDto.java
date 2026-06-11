package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del endpoint Summoner-V4 de Riot Games.
 *
 * Endpoint: GET /lol/summoner/v4/summoners/by-puuid/{encryptedPUUID}
 * Host:     {region}.api.riotgames.com  (ej: na1.api.riotgames.com)
 *
 * El campo más importante aquí es "id" (summonerId cifrado),
 * que necesitamos para consultar la clasificación en League-V4.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummonerDto {

    /** ID cifrado del invocador — requerido para consultar League-V4 */
    @JsonProperty("id")
    private String summonerId;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("puuid")
    private String puuid;

    /** Nombre legacy del invocador (puede estar vacío en cuentas nuevas post-Riot ID) */
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
