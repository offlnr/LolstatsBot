package com.lolbot.util;

import com.lolbot.models.AccountDto;
import com.lolbot.models.ClashTournamentDto;
import com.lolbot.models.LeagueEntryDto;
import com.lolbot.models.LiveGameDto;
import com.lolbot.models.MatchDto;
import com.lolbot.models.MatchParticipantDto;
import com.lolbot.models.SummonerDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StatsEmbedBuilder {

    private static final String DDRAGON_VERSION = "14.10.1";

    // Visual separator used as a blank divider field inside embeds
    private static final String SEP_NAME  = "​";
    private static final String SEP_VALUE = "​";

    private StatsEmbedBuilder() {}

    // =========================================================================
    // /stats
    // =========================================================================

    public static MessageEmbed buildStatsEmbed(
            AccountDto account,
            SummonerDto summoner,
            List<LeagueEntryDto> entries,
            String region) {

        Optional<LeagueEntryDto> soloEntry = entries.stream()
                .filter(e -> "RANKED_SOLO_5x5".equals(e.getQueueType()))
                .findFirst();

        Optional<LeagueEntryDto> flexEntry = entries.stream()
                .filter(e -> "RANKED_FLEX_SR".equals(e.getQueueType()))
                .findFirst();

        Color embedColor = soloEntry
                .map(e -> getTierColor(e.getTier()))
                .orElse(Color.GRAY);

        String profileIconUrl = String.format(
            "https://ddragon.leagueoflegends.com/cdn/%s/img/profileicon/%d.png",
            DDRAGON_VERSION, summoner.getProfileIconId()
        );

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(account.getGameName() + "  #" + account.getTagLine())
                .setColor(embedColor)
                .setThumbnail(profileIconUrl)
                .addField("REGION", region.toUpperCase(),                        true)
                .addField("LEVEL",  String.valueOf(summoner.getSummonerLevel()), true)
                .addBlankField(true)
                .addField(SEP_NAME, SEP_VALUE, false);

        soloEntry.ifPresentOrElse(
            entry -> embed.addField("SOLO / DUO", buildRankSection(entry), true),
            ()    -> embed.addField("SOLO / DUO", "_Unranked_",            true)
        );

        flexEntry.ifPresentOrElse(
            entry -> embed.addField("FLEX 5v5", buildRankSection(entry), true),
            ()    -> embed.addField("FLEX 5v5", "_Unranked_",            true)
        );

        embed.setFooter("Riot Games API  ·  " + region.toUpperCase(), null)
             .setTimestamp(Instant.now());

        return embed.build();
    }

    private static String buildRankSection(LeagueEntryDto entry) {
        StringBuilder sb = new StringBuilder();

        sb.append("**").append(entry.getFormattedRank()).append("**\n");
        sb.append("`").append(entry.getWins()).append("W  ")
          .append(entry.getLosses()).append("L`")
          .append("  ·  **").append(entry.getWinRate()).append("% WR**");

        if (entry.isHotStreak())  sb.append("\n_Hot streak_");
        if (entry.isFreshBlood()) sb.append("\n_Promoted_");
        if (entry.isVeteran())    sb.append("\n_Veteran_");

        return sb.toString();
    }

    // =========================================================================
    // /matches  &  /partidas
    // =========================================================================

    public static MessageEmbed buildMatchHistoryEmbed(
            AccountDto account,
            List<MatchDto> matches,
            String puuid,
            String platform) {

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Last " + matches.size() + " matches  —  "
                          + account.getGameName() + "  #" + account.getTagLine())
                .setColor(new Color(40, 100, 210))
                .setFooter("Riot Games API  ·  " + platform.toUpperCase(), null)
                .setTimestamp(Instant.now());

        for (MatchDto match : matches) {
            MatchDto.Info info = match.getInfo();

            MatchParticipantDto p = info.getParticipants().stream()
                    .filter(part -> puuid.equals(part.getPuuid()))
                    .findFirst()
                    .orElse(null);

            if (p == null) continue;

            // Field name: outcome tag + champion + mode + duration
            String outcome   = p.isWin() ? "WIN" : "LOSS";
            String fieldName = String.format("[%s]  %s  ·  %s  ·  %s",
                    outcome,
                    p.getChampionName(),
                    getQueueName(info.getQueueId()),
                    formatDuration(info.getGameDuration()));

            // Field value: KDA score in monospace, then secondary stats
            int    cs    = p.getTotalCS();
            String csStr = cs > 0 ? "  ·  " + cs + " CS" : "";
            String fieldValue = String.format("`%s`  ·  %s KDA%s  ·  %s",
                    p.getFormattedKda(),
                    p.getKdaRatio(),
                    csStr,
                    timeAgo(info.getGameCreation()));

            embed.addField(fieldName, fieldValue, false);
        }

        return embed.build();
    }

    // =========================================================================
    // /live
    // =========================================================================

    public static MessageEmbed buildLiveGameEmbed(AccountDto account,
                                                   LiveGameDto liveGame,
                                                   String puuid) {
        LiveGameDto.Participant targetParticipant = liveGame.getParticipants().stream()
                .filter(p -> puuid.equals(p.getPuuid()))
                .findFirst()
                .orElse(null);

        String targetChampion = targetParticipant != null
                ? ChampionCache.getChampionName(targetParticipant.getChampionId())
                : "Unknown";

        StringBuilder blueTeam = new StringBuilder();
        StringBuilder redTeam  = new StringBuilder();

        for (LiveGameDto.Participant p : liveGame.getParticipants()) {
            String  champName = ChampionCache.getChampionName(p.getChampionId());
            boolean isTarget  = puuid.equals(p.getPuuid());
            // Mark the queried player with a right arrow; others are plain
            String  line      = isTarget ? "**» " + champName + "**\n" : champName + "\n";
            if (p.getTeamId() == 100) blueTeam.append(line);
            else                      redTeam.append(line);
        }

        long   elapsed = liveGame.getGameLength();
        String timeStr = elapsed > 0
                ? String.format("%d:%02d", elapsed / 60, elapsed % 60)
                : "Loading...";

        return new EmbedBuilder()
                .setTitle(account.getGameName() + "  #" + account.getTagLine() + "  —  In Game")
                .setColor(new Color(200, 40, 40))
                .setDescription("**" + targetChampion + "**  ·  " + getQueueName(liveGame.getGameQueueConfigId()))
                .addField("BLUE SIDE", blueTeam.toString().trim(), true)
                .addField("RED SIDE",  redTeam.toString().trim(),  true)
                .addField("TIME",      "`" + timeStr + "`",        true)
                .setFooter("Riot Games API  ·  Spectator-V5", null)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed buildNotInGameEmbed(AccountDto account) {
        return new EmbedBuilder()
                .setTitle(account.getGameName() + "  #" + account.getTagLine())
                .setDescription("Not currently in an active game.")
                .setColor(new Color(100, 100, 110))
                .setFooter("Riot Games API  ·  Spectator-V5", null)
                .setTimestamp(Instant.now())
                .build();
    }

    // =========================================================================
    // /lastmatch
    // =========================================================================

    public static MessageEmbed buildLastMatchEmbed(AccountDto account,
                                                    MatchDto match,
                                                    String puuid) {
        MatchDto.Info info = match.getInfo();

        MatchParticipantDto p = info.getParticipants().stream()
                .filter(part -> puuid.equals(part.getPuuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PUUID not found in match " + match.getMetadata().getMatchId()));

        boolean win    = p.isWin();
        Color   color  = win ? new Color(0, 160, 85) : new Color(195, 40, 40);
        String  result = win ? "VICTORY" : "DEFEAT";

        // Division by zero guard: deaths == 0 is a perfect game
        String kdaRatio = p.getDeaths() == 0
                ? "Perfect"
                : String.format("%.2f", (double) (p.getKills() + p.getAssists()) / p.getDeaths());

        long   durationSec = info.getGameDuration();
        int    totalCS     = p.getTotalCS();
        double csPmin      = durationSec > 0 ? (totalCS * 60.0 / durationSec) : 0;

        return new EmbedBuilder()
                .setTitle(result + "  —  " + account.getGameName() + "  #" + account.getTagLine())
                .setColor(color)
                // Subtitle: champion · mode · duration
                .setDescription(String.format("**%s** (Lv. %d)  ·  %s  ·  %s",
                        p.getChampionName(), p.getChampLevel(),
                        getQueueName(info.getQueueId()), formatDuration(durationSec)))
                // Row 1: KDA | CS | DAMAGE
                .addField("KDA",
                        "`" + p.getFormattedKda() + "`\n**" + kdaRatio + "** ratio", true)
                .addField("CS",
                        "**" + totalCS + "**  (" + String.format("%.1f", csPmin) + "/min)", true)
                .addField("DAMAGE",
                        "**" + String.format("%,d", p.getTotalDamageDealtToChampions()) + "**", true)
                // Row 2: VISION SCORE | CONTROL WARDS | blank
                .addField("VISION SCORE",
                        "**" + p.getVisionScore() + "**", true)
                .addField("CONTROL WARDS",
                        "**" + p.getVisionWardsBoughtInGame() + "**  bought", true)
                .addBlankField(true)
                .setFooter(timeAgo(info.getGameCreation()) + "  ·  " + match.getMetadata().getMatchId(), null)
                .setTimestamp(Instant.now())
                .build();
    }

    // =========================================================================
    // /clash
    // =========================================================================

    private static final DateTimeFormatter CLASH_DATE_FMT = DateTimeFormatter
            .ofPattern("EEE, MMM d  —  HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneId.of("UTC"));

    public static MessageEmbed buildClashEmbed(List<ClashTournamentDto> tournaments,
                                                String platform) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Clash Schedule  —  " + platform.toUpperCase())
                .setColor(new Color(85, 45, 175))
                .setFooter("Riot Games API  ·  Clash-V1  ·  UTC", null)
                .setTimestamp(Instant.now());

        if (tournaments.isEmpty()) {
            embed.setDescription("No tournaments are currently scheduled for this region.");
            return embed.build();
        }

        for (ClashTournamentDto tournament : tournaments) {
            String name = formatTournamentName(tournament.getNameKey());

            if (tournament.getSchedule() == null || tournament.getSchedule().isEmpty()) {
                embed.addField(name.toUpperCase(), "_No phases scheduled._", false);
                continue;
            }

            StringBuilder sb = new StringBuilder();
            int day = 1;
            for (ClashTournamentDto.TournamentPhase phase : tournament.getSchedule()) {
                sb.append("**Day ").append(day).append("**");
                if (phase.isCancelled()) {
                    sb.append("  ~~Cancelled~~\n\n");
                } else {
                    String reg   = CLASH_DATE_FMT.format(Instant.ofEpochMilli(phase.getRegistrationTime()));
                    String start = CLASH_DATE_FMT.format(Instant.ofEpochMilli(phase.getStartTime()));
                    sb.append("\n");
                    sb.append("Registration  `").append(reg).append("`\n");
                    sb.append("Matches start  `").append(start).append("`\n\n");
                }
                day++;
            }

            embed.addField(name.toUpperCase(), sb.toString().trim(), false);
        }

        return embed.build();
    }

    // =========================================================================
    // /link  &  /unlink
    // =========================================================================

    public static MessageEmbed buildLinkSuccessEmbed(String gameName, String tagLine, String region) {
        return new EmbedBuilder()
                .setTitle("Account linked")
                .setDescription("**" + gameName + "  #" + tagLine + "**  ·  " + region.toUpperCase()
                        + "\nis now linked to your Discord profile.")
                .setColor(new Color(0, 160, 85))
                .setFooter("Use /unlink to remove this connection.", null)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed buildUnlinkSuccessEmbed(String gameName, String tagLine) {
        return new EmbedBuilder()
                .setTitle("Account unlinked")
                .setDescription("**" + gameName + "  #" + tagLine + "** has been removed from your Discord profile.")
                .setColor(new Color(100, 100, 110))
                .setFooter("Use /link to connect a new account.", null)
                .setTimestamp(Instant.now())
                .build();
    }

    // =========================================================================
    // Error embed
    // =========================================================================

    public static MessageEmbed buildErrorEmbed(String title, String message) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(message)
                .setColor(new Color(170, 25, 25))
                .setFooter("LoL Stats Bot", null)
                .setTimestamp(Instant.now())
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static String formatTournamentName(String nameKey) {
        if (nameKey == null || nameKey.isBlank()) return "Clash Tournament";
        String[] parts = nameKey.split("_");
        String   last  = parts[parts.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static String getQueueName(int queueId) {
        return switch (queueId) {
            case 420       -> "Ranked Solo";
            case 440       -> "Ranked Flex";
            case 400       -> "Normal Draft";
            case 430       -> "Normal Blind";
            case 450       -> "ARAM";
            case 490       -> "Quickplay";
            case 700       -> "Clash";
            case 900, 1900 -> "URF";
            case 1020      -> "One for All";
            case 1300      -> "Nexus Blitz";
            case 1700      -> "Arena";
            default        -> "Game";
        };
    }

    private static String formatDuration(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String timeAgo(long gameCreationMs) {
        long diffMin = (System.currentTimeMillis() - gameCreationMs) / 60_000;
        if (diffMin < 60)   return diffMin + "m ago";
        if (diffMin < 1440) return (diffMin / 60) + "h ago";
        long days = diffMin / 1440;
        if (days < 7)       return days + "d ago";
        return (days / 7) + "wk ago";
    }

    private static Color getTierColor(String tier) {
        if (tier == null) return Color.GRAY;
        return switch (tier.toUpperCase()) {
            case "IRON"        -> new Color(87,  75,  80);
            case "BRONZE"      -> new Color(140, 88,  50);
            case "SILVER"      -> new Color(150, 157, 169);
            case "GOLD"        -> new Color(205, 168, 72);
            case "PLATINUM"    -> new Color(55,  186, 169);
            case "EMERALD"     -> new Color(0,   154, 68);
            case "DIAMOND"     -> new Color(81,  145, 208);
            case "MASTER"      -> new Color(156, 82,  165);
            case "GRANDMASTER" -> new Color(215, 59,  59);
            case "CHALLENGER"  -> new Color(247, 201, 70);
            default            -> Color.GRAY;
        };
    }
}
