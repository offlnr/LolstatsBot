package com.lolbot.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolbot.models.AccountDto;
import com.lolbot.models.LeagueEntryDto;
import com.lolbot.models.MatchDto;
import com.lolbot.models.SummonerDto;
import com.lolbot.util.RegionUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servicio centralizado para toda comunicación con la API de Riot Games.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  FLUJO COMPLETO para obtener el rango de un jugador:            │
 * │                                                                 │
 * │  1. Account-V1  (cluster)   Riot ID ──→ PUUID                  │
 * │  2. Summoner-V4 (platform)  PUUID  ──→ summonerId + nivel       │
 * │  3. League-V4   (platform)  summonerId ──→ entradas de rango    │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Todos los métodos son SÍNCRONOS (bloqueantes). La asincronía se
 * gestiona desde {@link com.lolbot.commands.CommandManager} usando
 * un ExecutorService para no bloquear el hilo de eventos de JDA.
 */
public class RiotApiService {

    private static final Logger logger = LoggerFactory.getLogger(RiotApiService.class);

    private static final String CLUSTER_BASE_URL  = "https://%s.api.riotgames.com";
    private static final String PLATFORM_BASE_URL = "https://%s.api.riotgames.com";

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RiotApiService(String apiKey) {
        this.apiKey       = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient   = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15,    TimeUnit.SECONDS)
                .writeTimeout(10,   TimeUnit.SECONDS)
                .build();
    }

    // =========================================================================
    // PASO 1 — Account-V1: Riot ID → PUUID
    // =========================================================================

    /**
     * Consulta Account-V1 para obtener el PUUID a partir del Riot ID.
     *
     * El PUUID (Player Universally Unique Identifier) identifica al jugador
     * de forma global, independientemente de su región o servidor.
     *
     * Host de la petición: {cluster}.api.riotgames.com
     *  - americas  → NA, BR, LA1, LA2
     *  - europe    → EUW, EUNE, TR, RU
     *  - asia      → KR, JP
     *  - sea       → OCE y servidores del Sudeste Asiático
     *
     * @param gameName  Parte del nombre en el Riot ID, ej: "Faker"
     * @param tagLine   Parte del tag en el Riot ID,   ej: "KR1"
     * @param platform  Plataforma del jugador,        ej: "kr"
     * @throws RiotApiException si el jugador no existe (404) o hay error de API
     */
    public AccountDto getAccountByRiotId(String gameName, String tagLine, String platform)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);

        // URL-encodificamos gameName y tagLine para manejar espacios y caracteres especiales
        String encodedName = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
        String encodedTag  = URLEncoder.encode(tagLine,  StandardCharsets.UTF_8);

        String url = String.format(
            CLUSTER_BASE_URL + "/riot/account/v1/accounts/by-riot-id/%s/%s",
            cluster, encodedName, encodedTag
        );

        logger.info("Account-V1 → {}#{} [cluster: {}]", gameName, tagLine, cluster);
        return executeRequest(url, AccountDto.class);
    }

    // =========================================================================
    // PASO 2 — Summoner-V4: PUUID → datos del invocador
    // =========================================================================

    /**
     * Consulta Summoner-V4 para obtener los datos del invocador a partir del PUUID.
     *
     * El campo clave de la respuesta es "id" (summonerId cifrado), que se
     * necesita para la siguiente consulta a League-V4.
     *
     * Host de la petición: {platform}.api.riotgames.com
     *  - na1, euw1, kr, jp1, br1, la1, la2, tr1, ru, eun1, oc1
     *
     * @param puuid    PUUID global del jugador (obtenido en el paso anterior)
     * @param platform Plataforma del servidor, ej: "na1", "euw1", "kr"
     */
    public SummonerDto getSummonerByPuuid(String puuid, String platform)
            throws RiotApiException, IOException {

        String url = String.format(
            PLATFORM_BASE_URL + "/lol/summoner/v4/summoners/by-puuid/%s",
            platform, puuid
        );

        logger.info("Summoner-V4 → PUUID: {}... [platform: {}]", puuid.substring(0, 8), platform);
        return executeRequest(url, SummonerDto.class);
    }

    // =========================================================================
    // PASO 3 — League-V4: summonerId → entradas de clasificación
    // =========================================================================

    /**
     * Consulta League-V4 para obtener todas las entradas de clasificación del jugador.
     *
     * La API devuelve un ARRAY donde cada elemento es una cola diferente:
     *  - "RANKED_SOLO_5x5"  →  Solo / Duo
     *  - "RANKED_FLEX_SR"   →  Flex 5v5
     *
     * Si el jugador nunca jugó clasificatoria, el array estará vacío.
     * Si el jugador solo jugó una cola, habrá solo un elemento.
     *
     * @param summonerId ID cifrado del invocador (campo "id" del SummonerDto)
     * @param platform   Plataforma del servidor, ej: "na1"
     */
    public List<LeagueEntryDto> getLeagueEntries(String puuid, String platform)
            throws RiotApiException, IOException {

        String url = String.format(
            PLATFORM_BASE_URL + "/lol/league/v4/entries/by-puuid/%s",
            platform, puuid
        );

        logger.info("League-V4 → PUUID: {}... [platform: {}]", puuid.substring(0, 8), platform);

        // La respuesta de este endpoint es un array JSON, no un objeto.
        // Por eso deserializamos a LeagueEntryDto[] y luego lo convertimos a List.
        LeagueEntryDto[] entries = executeRequest(url, LeagueEntryDto[].class);
        return Arrays.asList(entries);
    }

    // =========================================================================
    // PASO 4 — Match-V5: PUUID → IDs de partidas recientes
    // =========================================================================

    public List<String> getMatchIds(String puuid, String platform, int count)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);
        String url = String.format(
            CLUSTER_BASE_URL + "/lol/match/v5/matches/by-puuid/%s/ids?start=0&count=%d",
            cluster, puuid, count
        );

        logger.info("Match-V5 IDs → PUUID: {}... [cluster: {}, count: {}]",
                puuid.substring(0, 8), cluster, count);
        String[] ids = executeRequest(url, String[].class);
        return Arrays.asList(ids);
    }

    // =========================================================================
    // PASO 5 — Match-V5: matchId → datos completos de la partida
    // =========================================================================

    public MatchDto getMatch(String matchId, String platform)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);
        String url = String.format(
            CLUSTER_BASE_URL + "/lol/match/v5/matches/%s",
            cluster, matchId
        );

        logger.info("Match-V5 → {} [cluster: {}]", matchId, cluster);
        return executeRequest(url, MatchDto.class);
    }

    // =========================================================================
    // Método central — ejecuta la petición HTTP y maneja los códigos de estado
    // =========================================================================

    /**
     * Ejecuta una petición GET autenticada a la API de Riot y deserializa la respuesta.
     *
     * @param url          URL completa del endpoint a consultar
     * @param responseType Clase Java a la que mapear el JSON de respuesta
     * @throws RiotApiException con el código HTTP y mensaje descriptivo en caso de error
     * @throws IOException      en caso de error de red o timeout
     */
    private <T> T executeRequest(String url, Class<T> responseType)
            throws RiotApiException, IOException {

        Request request = new Request.Builder()
                .url(url)
                // El API Key de Riot va siempre en el header X-Riot-Token
                .addHeader("X-Riot-Token", apiKey)
                .addHeader("Accept",       "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            // Códigos de error comunes de la API de Riot y sus causas:
            switch (response.code()) {
                case 200 -> { /* Todo correcto, continuamos */ }
                case 400 -> throw new RiotApiException(
                    "Solicitud inválida (400). Verifica el formato del Riot ID.", 400);
                case 401 -> throw new RiotApiException(
                    "API Key inválida o expirada (401). Genera una nueva en developer.riotgames.com.", 401);
                case 403 -> throw new RiotApiException(
                    "Sin permisos para este endpoint (403). Tu API Key podría ser de tipo Development.", 403);
                case 404 -> throw new RiotApiException(
                    "Jugador no encontrado (404). Verifica el Riot ID y la región.", 404);
                case 429 -> throw new RiotApiException(
                    "Límite de peticiones alcanzado (429). Espera unos segundos e inténtalo de nuevo.", 429);
                case 500 -> throw new RiotApiException(
                    "Error interno en los servidores de Riot (500). Intenta más tarde.", 500);
                case 503 -> throw new RiotApiException(
                    "Servicio de Riot no disponible (503). Intenta más tarde.", 503);
                default  -> throw new RiotApiException(
                    "Error inesperado de la API (" + response.code() + ").", response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new RiotApiException("La respuesta de la API estaba vacía.", 0);
            }

            return objectMapper.readValue(body.string(), responseType);
        }
    }

    // =========================================================================
    // Excepción interna
    // =========================================================================

    /**
     * Excepción lanzada cuando la API de Riot devuelve un código de error HTTP.
     * El código HTTP se expone para que el llamador pueda distinguir entre
     * un jugador no encontrado (404) y un error de autenticación (401/403).
     */
    public static class RiotApiException extends Exception {

        private final int httpCode;

        public RiotApiException(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }

        public int getHttpCode() { return httpCode; }
    }
}
