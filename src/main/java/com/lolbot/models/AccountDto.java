package com.lolbot.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del endpoint Account-V1 de Riot Games.
 *
 * Endpoint: GET /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}
 * Host:     {cluster}.api.riotgames.com  (ej: americas, europe, asia, sea)
 *
 * El PUUID es el identificador global del jugador, independiente de región.
 * Es el punto de partida para todas las consultas posteriores.
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

    /** Devuelve el Riot ID completo en formato "Nombre#TAG" */
    public String getFullRiotId() {
        return gameName + "#" + tagLine;
    }
}
