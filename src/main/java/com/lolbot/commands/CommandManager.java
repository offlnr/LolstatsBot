package com.lolbot.commands;

import com.lolbot.config.BotConfig;
import com.lolbot.models.AccountDto;
import com.lolbot.models.ClashTournamentDto;
import com.lolbot.models.LeagueEntryDto;
import com.lolbot.models.LiveGameDto;
import com.lolbot.models.MatchDto;
import com.lolbot.models.SummonerDto;
import com.lolbot.services.RiotApiService;
import com.lolbot.services.RiotApiService.RiotApiException;
import com.lolbot.util.RegionUtil;
import com.lolbot.util.StatsEmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central slash command handler for the bot.
 *
 * Extends ListenerAdapter to receive Discord events through JDA.
 *
 * Why deferReply() + ExecutorService:
 *   Discord requires an initial response within 3 seconds.
 *   Riot API calls can take 1-3 seconds each.
 *   deferReply() immediately sends a "thinking..." indicator,
 *   giving us up to 15 minutes to edit the response with real data.
 *
 *   HTTP requests run on a separate thread to avoid blocking JDA's
 *   event thread, which is shared across all events from all servers.
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final RiotApiService riotApiService;
    private final BotConfig      config;

    // Virtual thread pool (Java 21) — optimal for I/O-bound tasks like HTTP requests.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CommandManager(BotConfig config) {
        this.config         = config;
        this.riotApiService = new RiotApiService(config.getRiotApiKey());
    }

    // =========================================================================
    // Main listener — receives all slash commands
    // =========================================================================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        boolean knownCommand = switch (commandName) {
            case "stats", "matches", "live", "lastmatch", "clash" -> true;
            default -> false;
        };
        if (!knownCommand) return;

        // Respond immediately to satisfy Discord's 3-second deadline
        event.deferReply().queue();

        String regionInput = event.getOption("region") != null
                ? event.getOption("region").getAsString()
                : config.getDefaultRegion();
        String platform = RegionUtil.normalizePlatform(regionInput);

        // /clash has no summoner parameter
        if (commandName.equals("clash")) {
            logger.info("/clash invoked by '{}' [platform: {}]", event.getUser().getName(), platform);
            executor.submit(() -> processClashCommand(event, platform));
            return;
        }

        String riotIdInput = event.getOption("summoner").getAsString().trim();
        logger.info("/{} invoked by '{}' -> summoner='{}', platform='{}'",
                commandName, event.getUser().getName(), riotIdInput, platform);

        switch (commandName) {
            case "stats"     -> executor.submit(() -> processStatsCommand(event, riotIdInput, platform));
            case "live"      -> executor.submit(() -> processLiveCommand(event, riotIdInput, platform));
            case "lastmatch" -> executor.submit(() -> processLastMatchCommand(event, riotIdInput, platform));
            default          -> executor.submit(() -> processMatchesCommand(event, riotIdInput, platform));
        }
    }

    // =========================================================================
    // /stats — rank and summoner stats
    // =========================================================================

    private void processStatsCommand(SlashCommandInteractionEvent event,
                                     String riotIdInput,
                                     String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event,
                    "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/stats summoner:Faker#KR1`"
                );
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);
            SummonerDto summoner = riotApiService.getSummonerByPuuid(account.getPuuid(), platform);
            List<LeagueEntryDto> entries = riotApiService.getLeagueEntries(account.getPuuid(), platform);

            MessageEmbed statsEmbed = StatsEmbedBuilder.buildStatsEmbed(account, summoner, entries, platform);
            event.getHook().editOriginalEmbeds(statsEmbed).queue();

            logger.info("Stats response sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());

        } catch (IOException e) {
            logger.error("IOException querying API for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");

        } catch (Exception e) {
            logger.error("Unexpected error in /stats for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /matches & /partidas — last 10 match history
    // =========================================================================

    private void processMatchesCommand(SlashCommandInteractionEvent event,
                                       String riotIdInput,
                                       String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/matches summoner:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);

            List<String> matchIds = riotApiService.getMatchIds(account.getPuuid(), platform, 10);

            if (matchIds.isEmpty()) {
                sendError(event, "No matches found", "No recent matches found for this player.");
                return;
            }

            List<MatchDto> matches = new ArrayList<>();
            for (String matchId : matchIds) {
                matches.add(riotApiService.getMatch(matchId, platform));
            }

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildMatchHistoryEmbed(account, matches, account.getPuuid(), platform)
            ).queue();

            logger.info("Match history sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /matches for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
            logger.error("IOException in /matches for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /matches for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /live — active game check (Spectator-V5)
    // =========================================================================

    private void processLiveCommand(SlashCommandInteractionEvent event,
                                    String riotIdInput,
                                    String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/live summoner:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);

            java.util.Optional<LiveGameDto> liveGame =
                    riotApiService.getLiveGame(account.getPuuid(), platform);

            if (liveGame.isEmpty()) {
                // Normal state — player is simply not in a game
                event.getHook().editOriginalEmbeds(
                    StatsEmbedBuilder.buildNotInGameEmbed(account)
                ).queue();
                logger.info("Player '{}' is not in an active game.", riotIdInput);
                return;
            }

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildLiveGameEmbed(account, liveGame.get(), account.getPuuid())
            ).queue();
            logger.info("Live game embed sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /live for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (java.io.IOException e) {
            logger.error("IOException in /live for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /live for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /lastmatch — detailed breakdown of the most recent match (Match-V5)
    // =========================================================================

    private void processLastMatchCommand(SlashCommandInteractionEvent event,
                                         String riotIdInput,
                                         String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/lastmatch summoner:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            AccountDto account  = riotApiService.getAccountByRiotId(gameName, tagLine, platform);
            List<String> matchIds = riotApiService.getMatchIds(account.getPuuid(), platform, 1);

            if (matchIds.isEmpty()) {
                sendError(event, "No matches found",
                    "No recent matches were found for **" + riotIdInput + "**.");
                return;
            }

            MatchDto match = riotApiService.getMatch(matchIds.get(0), platform);

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildLastMatchEmbed(account, match, account.getPuuid())
            ).queue();
            logger.info("Last match embed sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /lastmatch for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (java.io.IOException e) {
            logger.error("IOException in /lastmatch for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /lastmatch for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /clash — upcoming tournament schedule (Clash-V1)
    // =========================================================================

    private void processClashCommand(SlashCommandInteractionEvent event, String platform) {
        try {
            List<ClashTournamentDto> tournaments = riotApiService.getClashTournaments(platform);

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildClashEmbed(tournaments, platform)
            ).queue();
            logger.info("Clash embed sent [platform: {}], {} tournament(s)", platform, tournaments.size());

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /clash [platform: {}]: {} [HTTP {}]",
                platform, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (java.io.IOException e) {
            logger.error("IOException in /clash [platform: {}]: {}", platform, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /clash [platform: {}]: {}", platform, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    private void sendError(SlashCommandInteractionEvent event, String title, String description) {
        event.getHook()
             .editOriginalEmbeds(StatsEmbedBuilder.buildErrorEmbed(title, description))
             .queue();
    }
}
