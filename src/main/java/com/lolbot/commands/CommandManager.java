package com.lolbot.commands;

import com.lolbot.config.BotConfig;
import com.lolbot.models.AccountDto;
import com.lolbot.models.ClashTournamentDto;
import com.lolbot.models.LeagueEntryDto;
import com.lolbot.models.LiveGameDto;
import com.lolbot.models.MatchDto;
import com.lolbot.models.SummonerDto;
import com.lolbot.models.UserLinkDto;
import com.lolbot.services.RiotApiService;
import com.lolbot.services.RiotApiService.RiotApiException;
import com.lolbot.services.UserLinkService;
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
 * Central slash command handler.
 *
 * Why deferReply() + ExecutorService:
 *   Discord requires a response within 3 seconds. Riot API calls can take longer.
 *   deferReply() sends a "thinking..." indicator immediately, giving up to 15 minutes
 *   to deliver the actual response. All API work runs on virtual threads so JDA's
 *   event thread is never blocked.
 *
 * Summoner resolution order for stat commands (/stats, /matches, /live, /lastmatch):
 *   1. If the user passed the `summoner` option explicitly, use it.
 *   2. Otherwise, look up the Discord user's linked Riot account via UserLinkService.
 *   3. If neither is available, respond with a prompt to use /link.
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final RiotApiService  riotApiService;
    private final UserLinkService userLinkService;
    private final BotConfig       config;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CommandManager(BotConfig config) {
        this.config          = config;
        this.riotApiService  = new RiotApiService(config.getRiotApiKey());
        this.userLinkService = new UserLinkService("data");
    }

    // =========================================================================
    // Main router
    // =========================================================================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        boolean knownCommand = switch (commandName) {
            case "stats", "matches", "live", "lastmatch", "clash", "link", "unlink" -> true;
            default -> false;
        };
        if (!knownCommand) return;

        event.deferReply().queue();

        // --- Commands with no summoner parameter ---
        if (commandName.equals("clash")) {
            String platform = resolvePlatform(event);
            logger.info("/clash invoked by '{}' [platform: {}]", event.getUser().getName(), platform);
            executor.submit(() -> processClashCommand(event, platform));
            return;
        }

        if (commandName.equals("unlink")) {
            executor.submit(() -> processUnlinkCommand(event));
            return;
        }

        // --- /link: summoner is required, supplied directly ---
        if (commandName.equals("link")) {
            String riotIdInput = event.getOption("summoner").getAsString().trim();
            String platform    = resolvePlatform(event);
            logger.info("/link invoked by '{}' -> summoner='{}', platform='{}'",
                    event.getUser().getName(), riotIdInput, platform);
            executor.submit(() -> processLinkCommand(event, riotIdInput, platform));
            return;
        }

        // --- Stat commands: 'me' triggers linked-account lookup, anything else is a literal Riot ID ---
        String raw = event.getOption("summoner").getAsString().trim();

        String riotIdInput;
        String platform;

        if (raw.equalsIgnoreCase("me")) {
            UserLinkDto linked = userLinkService.getLink(event.getUser().getId());
            if (linked == null) {
                event.getHook().editOriginalEmbeds(
                    StatsEmbedBuilder.buildErrorEmbed(
                        "No account linked",
                        "You don't have a Riot account linked to your Discord profile.\n" +
                        "Use `/link summoner:YourName#TAG` to connect your account first."
                    )
                ).queue();
                return;
            }
            riotIdInput = linked.getRiotId();
            platform    = linked.getRegion();
            logger.info("/{} invoked by '{}' using linked account '{}'  [{}]",
                    commandName, event.getUser().getName(), riotIdInput, platform);
        } else {
            riotIdInput = raw;
            platform    = resolvePlatform(event);
            logger.info("/{} invoked by '{}' -> summoner='{}', platform='{}'",
                    commandName, event.getUser().getName(), riotIdInput, platform);
        }

        switch (commandName) {
            case "stats"     -> executor.submit(() -> processStatsCommand(event, riotIdInput, platform));
            case "matches"   -> executor.submit(() -> processMatchesCommand(event, riotIdInput, platform));
            case "live"      -> executor.submit(() -> processLiveCommand(event, riotIdInput, platform));
            case "lastmatch" -> executor.submit(() -> processLastMatchCommand(event, riotIdInput, platform));
        }
    }

    /** Reads the region option, falling back to the configured default. */
    private String resolvePlatform(SlashCommandInteractionEvent event) {
        String regionInput = event.getOption("region") != null
                ? event.getOption("region").getAsString()
                : config.getDefaultRegion();
        return RegionUtil.normalizePlatform(regionInput);
    }

    // =========================================================================
    // /link
    // =========================================================================

    private void processLinkCommand(SlashCommandInteractionEvent event,
                                     String riotIdInput,
                                     String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/link summoner:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            // Verify the account actually exists before saving the link
            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);

            userLinkService.link(event.getUser().getId(), riotIdInput, platform);

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildLinkSuccessEmbed(account.getGameName(), account.getTagLine(), platform)
            ).queue();

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /link for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
            logger.error("IOException in /link for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /link for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /unlink
    // =========================================================================

    private void processUnlinkCommand(SlashCommandInteractionEvent event) {
        UserLinkDto linked = userLinkService.getLink(event.getUser().getId());

        if (linked == null) {
            sendError(event, "No account linked",
                "You don't have a Riot account linked to your Discord profile.");
            return;
        }

        // Split stored riotId to display name and tag in the confirmation embed
        String[] parts    = linked.getRiotId().split("#", 2);
        String   gameName = parts[0];
        String   tagLine  = parts.length > 1 ? parts[1] : "";

        userLinkService.unlink(event.getUser().getId());

        event.getHook().editOriginalEmbeds(
            StatsEmbedBuilder.buildUnlinkSuccessEmbed(gameName, tagLine)
        ).queue();
    }

    // =========================================================================
    // /stats
    // =========================================================================

    private void processStatsCommand(SlashCommandInteractionEvent event,
                                     String riotIdInput,
                                     String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Invalid format",
                    "Riot ID must follow the format `Name#TAG`\n" +
                    "Example: `/stats summoner:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Invalid format", "Name and tag cannot be empty.");
                return;
            }

            AccountDto           account  = riotApiService.getAccountByRiotId(gameName, tagLine, platform);
            SummonerDto          summoner = riotApiService.getSummonerByPuuid(account.getPuuid(), platform);
            List<LeagueEntryDto> entries  = riotApiService.getLeagueEntries(account.getPuuid(), platform);

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildStatsEmbed(account, summoner, entries, platform)
            ).queue();

            logger.info("Stats sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /stats for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
            logger.error("IOException in /stats for '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /stats for '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================
    // /matches
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

            AccountDto   account  = riotApiService.getAccountByRiotId(gameName, tagLine, platform);
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
    // /live
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
                event.getHook().editOriginalEmbeds(
                    StatsEmbedBuilder.buildNotInGameEmbed(account)
                ).queue();
                return;
            }

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildLiveGameEmbed(account, liveGame.get(), account.getPuuid())
            ).queue();

            logger.info("Live game sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /live for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
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
    // /lastmatch
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

            AccountDto   account  = riotApiService.getAccountByRiotId(gameName, tagLine, platform);
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

            logger.info("Last match sent for '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /lastmatch for '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
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
    // /clash
    // =========================================================================

    private void processClashCommand(SlashCommandInteractionEvent event, String platform) {
        try {
            List<ClashTournamentDto> tournaments = riotApiService.getClashTournaments(platform);

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildClashEmbed(tournaments, platform)
            ).queue();

            logger.info("Clash sent [platform: {}], {} tournament(s)", platform, tournaments.size());

        } catch (RiotApiException e) {
            logger.warn("RiotApiException in /clash [{}]: {} [HTTP {}]",
                platform, e.getMessage(), e.getHttpCode());
            sendError(event, "Riot API Error", e.getMessage());
        } catch (IOException e) {
            logger.error("IOException in /clash [{}]: {}", platform, e.getMessage());
            sendError(event, "Connection Error",
                "Could not reach Riot Games servers. Please try again in a moment.");
        } catch (Exception e) {
            logger.error("Unexpected error in /clash [{}]: {}", platform, e.getMessage(), e);
            sendError(event, "Unexpected Error",
                "An internal error occurred. If the problem persists, contact the bot administrator.");
        }
    }

    // =========================================================================

    private void sendError(SlashCommandInteractionEvent event, String title, String description) {
        event.getHook()
             .editOriginalEmbeds(StatsEmbedBuilder.buildErrorEmbed(title, description))
             .queue();
    }
}
