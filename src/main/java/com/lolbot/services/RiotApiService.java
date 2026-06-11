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
 * Centralized service for all communication with the Riot Games API.
 *
 * Full flow to retrieve a player's rank:
 *  1. Account-V1  (cluster)   Riot ID   -> PUUID
 *  2. Summoner-V4 (platform)  PUUID     -> summoner level + icon
 *  3. League-V4   (platform)  PUUID     -> ranked entries
 *
 * Full flow to retrieve match history:
 *  4. Match-V5    (cluster)   PUUID     -> list of match IDs
 *  5. Match-V5    (cluster)   match ID  -> match details
 *
 * All methods are synchronous. Async execution is handled by CommandManager
 * via a virtual-thread ExecutorService so JDA's event thread is never blocked.
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
    // Step 1 — Account-V1: Riot ID -> PUUID
    // =========================================================================

    /**
     * Queries Account-V1 to obtain the PUUID from a Riot ID.
     *
     * The PUUID (Player Universally Unique Identifier) identifies the player
     * globally, regardless of region or server.
     *
     * Request host: {cluster}.api.riotgames.com
     *  - americas -> NA, BR, LAN, LAS
     *  - europe   -> EUW, EUNE, TR, RU
     *  - asia     -> KR, JP
     *  - sea      -> OCE and Southeast Asia servers
     */
    public AccountDto getAccountByRiotId(String gameName, String tagLine, String platform)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);

        String encodedName = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
        String encodedTag  = URLEncoder.encode(tagLine,  StandardCharsets.UTF_8);

        String url = String.format(
            CLUSTER_BASE_URL + "/riot/account/v1/accounts/by-riot-id/%s/%s",
            cluster, encodedName, encodedTag
        );

        logger.info("Account-V1 -> {}#{} [cluster: {}]", gameName, tagLine, cluster);
        return executeRequest(url, AccountDto.class);
    }

    // =========================================================================
    // Step 2 — Summoner-V4: PUUID -> summoner data
    // =========================================================================

    /**
     * Queries Summoner-V4 to retrieve summoner level and profile icon.
     *
     * Request host: {platform}.api.riotgames.com
     *  - na1, euw1, kr, jp1, br1, la1, la2, tr1, ru, eun1, oc1
     */
    public SummonerDto getSummonerByPuuid(String puuid, String platform)
            throws RiotApiException, IOException {

        String url = String.format(
            PLATFORM_BASE_URL + "/lol/summoner/v4/summoners/by-puuid/%s",
            platform, puuid
        );

        logger.info("Summoner-V4 -> PUUID: {}... [platform: {}]", puuid.substring(0, 8), platform);
        return executeRequest(url, SummonerDto.class);
    }

    // =========================================================================
    // Step 3 — League-V4: PUUID -> ranked entries
    // =========================================================================

    /**
     * Queries League-V4 to retrieve all ranked entries for a player.
     *
     * The API returns an array where each element is a different queue:
     *  - "RANKED_SOLO_5x5" -> Solo/Duo
     *  - "RANKED_FLEX_SR"  -> Flex 5v5
     *
     * Returns an empty list if the player has never played ranked.
     */
    public List<LeagueEntryDto> getLeagueEntries(String puuid, String platform)
            throws RiotApiException, IOException {

        String url = String.format(
            PLATFORM_BASE_URL + "/lol/league/v4/entries/by-puuid/%s",
            platform, puuid
        );

        logger.info("League-V4 -> PUUID: {}... [platform: {}]", puuid.substring(0, 8), platform);
        LeagueEntryDto[] entries = executeRequest(url, LeagueEntryDto[].class);
        return Arrays.asList(entries);
    }

    // =========================================================================
    // Step 4 — Match-V5: PUUID -> recent match IDs
    // =========================================================================

    public List<String> getMatchIds(String puuid, String platform, int count)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);
        String url = String.format(
            CLUSTER_BASE_URL + "/lol/match/v5/matches/by-puuid/%s/ids?start=0&count=%d",
            cluster, puuid, count
        );

        logger.info("Match-V5 IDs -> PUUID: {}... [cluster: {}, count: {}]",
                puuid.substring(0, 8), cluster, count);
        String[] ids = executeRequest(url, String[].class);
        return Arrays.asList(ids);
    }

    // =========================================================================
    // Step 5 — Match-V5: matchId -> full match data
    // =========================================================================

    public MatchDto getMatch(String matchId, String platform)
            throws RiotApiException, IOException {

        String cluster = RegionUtil.getCluster(platform);
        String url = String.format(
            CLUSTER_BASE_URL + "/lol/match/v5/matches/%s",
            cluster, matchId
        );

        logger.info("Match-V5 -> {} [cluster: {}]", matchId, cluster);
        return executeRequest(url, MatchDto.class);
    }

    // =========================================================================
    // Core HTTP method
    // =========================================================================

    /**
     * Executes an authenticated GET request to the Riot API and deserializes the response.
     *
     * @param url          Full endpoint URL
     * @param responseType Java class to map the JSON response to
     * @throws RiotApiException with the HTTP code and a descriptive message on API errors
     * @throws IOException      on network errors or timeouts
     */
    private <T> T executeRequest(String url, Class<T> responseType)
            throws RiotApiException, IOException {

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Riot-Token", apiKey)
                .addHeader("Accept",       "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            switch (response.code()) {
                case 200 -> { /* success */ }
                case 400 -> throw new RiotApiException(
                    "Invalid request (400). Check the Riot ID format.", 400);
                case 401 -> throw new RiotApiException(
                    "Invalid or expired API key (401). Generate a new one at developer.riotgames.com.", 401);
                case 403 -> throw new RiotApiException(
                    "No permissions for this endpoint (403). Your key may be a Development type.", 403);
                case 404 -> throw new RiotApiException(
                    "Player not found (404). Check the Riot ID and region.", 404);
                case 429 -> throw new RiotApiException(
                    "Rate limit exceeded (429). Please wait a few seconds and try again.", 429);
                case 500 -> throw new RiotApiException(
                    "Riot internal server error (500). Please try again later.", 500);
                case 503 -> throw new RiotApiException(
                    "Riot service unavailable (503). Please try again later.", 503);
                default  -> throw new RiotApiException(
                    "Unexpected API error (" + response.code() + ").", response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new RiotApiException("API response was empty.", 0);
            }

            return objectMapper.readValue(body.string(), responseType);
        }
    }

    // =========================================================================
    // Internal exception
    // =========================================================================

    /**
     * Thrown when the Riot API returns an HTTP error code.
     * The HTTP code is exposed so callers can distinguish between
     * a missing player (404) and an auth failure (401/403).
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
