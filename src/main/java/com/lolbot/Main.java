package com.lolbot;

import com.lolbot.commands.CommandManager;
import com.lolbot.config.BotConfig;
import com.lolbot.listeners.ReadyListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for LoL Stats Bot.
 *
 * Responsibilities:
 *  1. Load configuration (tokens and API keys from config.properties or env vars).
 *  2. Build the JDA instance with minimal intents and cache flags.
 *  3. Register event listeners (ReadyListener, CommandManager).
 *  4. Wait for JDA to be ready, then register slash commands.
 *
 * Gateway Intents note:
 *  Privileged intents (GUILD_MEMBERS, MESSAGE_CONTENT, GUILD_PRESENCES) are not
 *  needed for a slash-command-only bot and are therefore not requested.
 *  createLight() covers the minimum set required for basic operation.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting LoL Stats Bot...");

        BotConfig config = BotConfig.load();

        try {
            JDA jda = JDABuilder
                    .createLight(config.getDiscordToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.SCHEDULED_EVENTS,
                            CacheFlag.MEMBER_OVERRIDES
                    )
                    .addEventListeners(
                            new ReadyListener(),
                            new CommandManager(config)
                    )
                    .build();

            jda.awaitReady();
            registerSlashCommands(jda);

        } catch (InterruptedException e) {
            logger.error("Main thread interrupted during JDA initialization.", e);
            Thread.currentThread().interrupt();
            System.exit(1);
        }
    }

    /**
     * Registers all global slash commands with Discord.
     * Must be called after awaitReady() to ensure JDA is authenticated.
     *
     * Global commands take up to 1 hour to propagate. For faster testing,
     * replace jda.updateCommands() with jda.getGuildById("GUILD_ID").updateCommands().
     */
    private static void registerSlashCommands(JDA jda) {
        jda.updateCommands()
            .addCommands(

                Commands.slash("stats", "Shows the rank and stats of a League of Legends summoner")
                    .addOption(OptionType.STRING, "summoner",
                        "Full Riot ID of the player (e.g. Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Player's server (e.g. na1, euw1, kr, la2, br1). Default: la1", false),

                Commands.slash("matches", "Shows the last 10 matches for a player")
                    .addOption(OptionType.STRING, "summoner",
                        "Full Riot ID of the player (e.g. Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Player's server (e.g. na1, euw1, kr, la2, br1). Default: la1", false),

                Commands.slash("live", "Check if a player is currently in a live game")
                    .addOption(OptionType.STRING, "summoner",
                        "Full Riot ID of the player (e.g. Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Player's server (e.g. na1, euw1, kr, la2, br1). Default: la1", false),

                Commands.slash("lastmatch", "Detailed breakdown of a player's most recent match")
                    .addOption(OptionType.STRING, "summoner",
                        "Full Riot ID of the player (e.g. Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Player's server (e.g. na1, euw1, kr, la2, br1). Default: la1", false),

                Commands.slash("clash", "Show upcoming Clash tournament schedule for a region")
                    .addOption(OptionType.STRING, "region",
                        "Player's server (e.g. na1, euw1, kr, la2, br1). Default: la1", false)

            )
            .queue(
                success -> logger.info("Slash commands registered: {} command(s).", success.size()),
                failure -> logger.error("Failed to register slash commands: {}", failure.getMessage())
            );
    }
}
