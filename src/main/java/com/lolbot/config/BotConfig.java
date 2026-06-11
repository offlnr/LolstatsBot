package com.lolbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Carga y provee acceso a la configuración del bot.
 *
 * Prioridad de resolución (mayor a menor):
 *  1. Variables de entorno del sistema operativo  →  ideal para Docker/servidores
 *  2. Archivo src/main/resources/config.properties →  ideal para desarrollo local
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

    /**
     * Carga la configuración y valida que los valores obligatorios estén presentes.
     * Lanza {@link IllegalStateException} con un mensaje descriptivo si falta algo.
     */
    public static BotConfig load() {
        Properties props = loadPropertiesFile();

        String discordToken  = resolveProperty("DISCORD_TOKEN",  props);
        String riotApiKey    = resolveProperty("RIOT_API_KEY",   props);
        String defaultRegion = resolveProperty("DEFAULT_REGION", props, "la1");

        validateToken(discordToken, "DISCORD_TOKEN");
        validateToken(riotApiKey,   "RIOT_API_KEY");

        logger.info("Configuración cargada. Región por defecto: {}", defaultRegion);
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
                logger.warn("No se encontró config.properties. Se usarán variables de entorno.");
            }
        } catch (IOException e) {
            logger.warn("Error leyendo config.properties: {}", e.getMessage());
        }
        return props;
    }

    /** Busca primero en variables de entorno; si no, en el archivo de propiedades. */
    private static String resolveProperty(String key, Properties props) {
        return resolveProperty(key, props, null);
    }

    private static String resolveProperty(String key, Properties props, String defaultValue) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String propValue = props.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.trim();
        }
        return defaultValue;
    }

    private static void validateToken(String value, String name) {
        if (value == null || value.isBlank() || value.startsWith("TU_")) {
            throw new IllegalStateException(
                "[CONFIG] Falta configurar '" + name + "'. " +
                "Defínelo en config.properties o como variable de entorno."
            );
        }
    }

    // -------------------------------------------------------------------------

    public String getDiscordToken()  { return discordToken;  }
    public String getRiotApiKey()    { return riotApiKey;    }
    public String getDefaultRegion() { return defaultRegion; }
}
