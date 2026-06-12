package com.lolbot.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolbot.models.UserLinkDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the persistent mapping between Discord user IDs and their linked Riot accounts.
 *
 * Storage: data/user-links.json (JSON object, key = Discord user ID as string).
 * All public methods are synchronized — safe for concurrent virtual-thread access.
 */
public class UserLinkService {

    private static final Logger logger = LoggerFactory.getLogger(UserLinkService.class);
    private static final TypeReference<Map<String, UserLinkDto>> MAP_TYPE = new TypeReference<>() {};

    private final Path filePath;
    private final ObjectMapper mapper;
    private final Map<String, UserLinkDto> links;

    public UserLinkService(String dataDirectory) {
        this.filePath = Path.of(dataDirectory, "user-links.json");
        this.mapper   = new ObjectMapper();
        this.links    = loadFromFile();
    }

    /**
     * Creates or replaces the link for a Discord user.
     *
     * @param discordUserId Discord snowflake ID of the user
     * @param riotId        Full Riot ID string ("Name#TAG")
     * @param region        Normalized platform string ("na1", "euw1", etc.)
     */
    public synchronized void link(String discordUserId, String riotId, String region) {
        links.put(discordUserId, new UserLinkDto(riotId, region));
        saveToFile();
        logger.info("Linked Discord user {} -> {}  [{}]", discordUserId, riotId, region);
    }

    /**
     * Removes the link for a Discord user.
     *
     * @return true if a link existed and was removed, false if the user had no link
     */
    public synchronized boolean unlink(String discordUserId) {
        boolean existed = links.remove(discordUserId) != null;
        if (existed) {
            saveToFile();
            logger.info("Unlinked Discord user {}", discordUserId);
        }
        return existed;
    }

    /**
     * Returns the linked account for a Discord user, or null if none exists.
     */
    public synchronized UserLinkDto getLink(String discordUserId) {
        return links.get(discordUserId);
    }

    // -------------------------------------------------------------------------

    private Map<String, UserLinkDto> loadFromFile() {
        if (!Files.exists(filePath)) {
            logger.info("data/user-links.json not found — starting with empty link table.");
            return new HashMap<>();
        }
        try {
            Map<String, UserLinkDto> loaded = mapper.readValue(filePath.toFile(), MAP_TYPE);
            logger.info("Loaded {} user link(s) from {}", loaded.size(), filePath);
            return new HashMap<>(loaded);
        } catch (IOException e) {
            logger.error("Failed to read user-links.json: {} — starting fresh.", e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveToFile() {
        try {
            Files.createDirectories(filePath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), links);
        } catch (IOException e) {
            logger.error("Failed to write user-links.json: {}", e.getMessage());
        }
    }
}
