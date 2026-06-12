package com.lolbot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves champion IDs to champion display names using Riot's Data Dragon CDN.
 *
 * Load strategy:
 *  1. On first call to getChampionName(), fetch the latest patch version from DDragon.
 *  2. Download the champion list JSON for that version.
 *  3. Build an immutable Map<Integer, String> of (championId -> displayName).
 *  4. Cache it for the bot's entire lifetime — the map is stable within a patch.
 *
 * If DDragon is unreachable, the method returns "Champion {id}" as a safe fallback
 * and the cache will retry on the next invocation.
 */
public final class ChampionCache {

    private static final Logger logger = LoggerFactory.getLogger(ChampionCache.class);

    private static final String VERSIONS_URL  = "https://ddragon.leagueoflegends.com/api/versions.json";
    private static final String CHAMPIONS_URL = "https://ddragon.leagueoflegends.com/cdn/%s/data/en_US/champion.json";

    private static volatile Map<Integer, String> championMap = Collections.emptyMap();
    private static final AtomicBoolean           loaded      = new AtomicBoolean(false);

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10,    TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private ChampionCache() {}

    /**
     * Returns the display name for the given champion ID.
     * Triggers a lazy load from DDragon on the first call.
     */
    public static String getChampionName(int championId) {
        if (!loaded.get()) {
            load();
        }
        return championMap.getOrDefault(championId, "Champion " + championId);
    }

    private static synchronized void load() {
        if (loaded.get()) return; // second thread arrived after the first finished

        try {
            String latestPatch = fetchLatestPatch();
            Map<Integer, String> map = fetchChampionMap(latestPatch);

            championMap = Collections.unmodifiableMap(map);
            loaded.set(true);
            logger.info("Champion cache loaded: {} champions (patch {})", map.size(), latestPatch);

        } catch (Exception e) {
            // Don't set loaded=true — allow a retry on the next call.
            logger.warn("Failed to load champion cache from DDragon: {}", e.getMessage());
        }
    }

    private static String fetchLatestPatch() throws Exception {
        Request req = new Request.Builder().url(VERSIONS_URL).build();
        try (Response res = httpClient.newCall(req).execute()) {
            JsonNode versions = mapper.readTree(res.body().string());
            return versions.get(0).asText(); // index 0 is always the latest patch
        }
    }

    private static Map<Integer, String> fetchChampionMap(String patch) throws Exception {
        String url = String.format(CHAMPIONS_URL, patch);
        Request req = new Request.Builder().url(url).build();
        try (Response res = httpClient.newCall(req).execute()) {
            JsonNode root = mapper.readTree(res.body().string());
            JsonNode data = root.get("data");

            Map<Integer, String> map = new HashMap<>();
            data.fields().forEachRemaining(entry -> {
                // "key" is the numeric champion ID stored as a string in DDragon
                int    id   = entry.getValue().get("key").asInt();
                String name = entry.getValue().get("name").asText();
                map.put(id, name);
            });
            return map;
        }
    }
}
