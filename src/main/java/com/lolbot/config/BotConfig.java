package com.lolbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and exposes bot configuration.
 *
 * Resolution priority (highest to lowest):
 *  1. OS environment variables  — preferred for Docker / production servers
 *  2. src/main/resources/config.properties — preferred for local development
 */
public class BotConfig {

    private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);

    private final String discordToken;
    private final String riotApiKey;
    private final String defaultRegion;

    private BotConfig(String discordToken, String riotApiKey, String defaultRegion) {
        this.discordToken  = discordToken;
        this.riotApiKey    = riotApiKey;
        this.defaultRegion = defaultRegion;
    }

    public static BotConfig load() {
        Properties props = loadPropertiesFile();

        String discordToken  = resolveProperty("DISCORD_TOKEN",  props);
        String riotApiKey    = resolveProperty("RIOT_API_KEY",   props);
        String defaultRegion = resolveProperty("DEFAULT_REGION", props, "la1");

        validateToken(discordToken, "DISCORD_TOKEN");
        validateToken(riotApiKey,   "RIOT_API_KEY");

        logger.info("Configuration loaded. Default region: {}", defaultRegion);
        return new BotConfig(discordToken, riotApiKey, defaultRegion);
    }

    // -------------------------------------------------------------------------

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        try (InputStream is = BotConfig.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                logger.warn("config.properties not found. Falling back to environment variables.");
            }
        } catch (IOException e) {
            logger.warn("Error reading config.properties: {}", e.getMessage());
        }
        return props;
    }

    private static String resolveProperty(String key, Properties props) {
        return resolveProperty(key, props, null);
    }

    private static String resolveProperty(String key, Properties props, String defaultValue) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) return envValue.trim();
        String propValue = props.getProperty(key);
        if (propValue != null && !propValue.isBlank()) return propValue.trim();
        return defaultValue;
    }

    private static void validateToken(String value, String name) {
        if (value == null || value.isBlank() || value.startsWith("your_")) {
            throw new IllegalStateException(
                "[CONFIG] Missing required property '" + name + "'. " +
                "Set it in config.properties or as an environment variable."
            );
        }
    }

    // -------------------------------------------------------------------------

    public String getDiscordToken()  { return discordToken;  }
    public String getRiotApiKey()    { return riotApiKey;    }
    public String getDefaultRegion() { return defaultRegion; }
}
