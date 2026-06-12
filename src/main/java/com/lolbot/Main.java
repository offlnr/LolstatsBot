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

    private static void registerSlashCommands(JDA jda) {
        // Summoner field description explains the 'me' shortcut so users discover it naturally
        String summonerDesc = "Riot ID (e.g. Faker#KR1), or 'me' to use your linked account";
        String regionDesc   = "Server (e.g. na1, euw1, kr, la2, br1). Default: la1";

        jda.updateCommands()
            .addCommands(

                Commands.slash("stats", "Shows the rank and stats of a summoner")
                    .addOption(OptionType.STRING, "summoner", summonerDesc, true)
                    .addOption(OptionType.STRING, "region",   regionDesc,   false),

                Commands.slash("matches", "Shows the last 10 matches for a player")
                    .addOption(OptionType.STRING, "summoner", summonerDesc, true)
                    .addOption(OptionType.STRING, "region",   regionDesc,   false),

                Commands.slash("live", "Check if a player is currently in a live game")
                    .addOption(OptionType.STRING, "summoner", summonerDesc, true)
                    .addOption(OptionType.STRING, "region",   regionDesc,   false),

                Commands.slash("lastmatch", "Detailed breakdown of the most recent match")
                    .addOption(OptionType.STRING, "summoner", summonerDesc, true)
                    .addOption(OptionType.STRING, "region",   regionDesc,   false),

                Commands.slash("clash", "Show upcoming Clash tournament schedule for a region")
                    .addOption(OptionType.STRING, "region", regionDesc, false),

                Commands.slash("link", "Link your Riot account to your Discord profile")
                    .addOption(OptionType.STRING, "summoner", "Your Riot ID (e.g. YourName#TAG)", true)
                    .addOption(OptionType.STRING, "region",   regionDesc, false),

                Commands.slash("unlink", "Unlink your Riot account from your Discord profile")

            )
            .queue(
                success -> logger.info("Slash commands registered: {} command(s).", success.size()),
                failure -> logger.error("Failed to register slash commands: {}", failure.getMessage())
            );
    }
}
