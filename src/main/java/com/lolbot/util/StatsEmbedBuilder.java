package com.lolbot.util;

import com.lolbot.models.AccountDto;
import com.lolbot.models.LeagueEntryDto;
import com.lolbot.models.MatchDto;
import com.lolbot.models.MatchParticipantDto;
import com.lolbot.models.SummonerDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Construye los embeds de Discord para mostrar las estadísticas de LoL.
 * Un embed es el "recuadro enriquecido" con color, imagen y campos que
 * Discord renderiza cuando el bot responde.
 */
public final class StatsEmbedBuilder {

    // Versión del parche usada para las imágenes del Data Dragon de Riot.
    // Actualizar cuando salga un nuevo parche: https://ddragon.leagueoflegends.com/api/versions.json
    private static final String DDRAGON_VERSION = "14.10.1";

    private StatsEmbedBuilder() { /* clase de utilidad, no instanciar */ }

    /**
     * Construye el embed principal con las estadísticas completas del jugador.
     *
     * @param account  Datos de la cuenta (gameName, tagLine) del Account-V1
     * @param summoner Datos del invocador (nivel, iconId) del Summoner-V4
     * @param entries  Lista de entradas clasificatorias del League-V4
     * @param region   Región consultada para mostrársela al usuario
     */
    public static MessageEmbed buildStatsEmbed(
            AccountDto account,
            SummonerDto summoner,
            List<LeagueEntryDto> entries,
            String region) {

        // Buscamos las entradas de Solo/Duo y Flex separadamente del array
        Optional<LeagueEntryDto> soloEntry = entries.stream()
                .filter(e -> "RANKED_SOLO_5x5".equals(e.getQueueType()))
                .findFirst();

        Optional<LeagueEntryDto> flexEntry = entries.stream()
                .filter(e -> "RANKED_FLEX_SR".equals(e.getQueueType()))
                .findFirst();

        // El color del embed refleja el tier de Solo/Duo; gris si no tiene clasificatoria
        Color embedColor = soloEntry
                .map(e -> getTierColor(e.getTier()))
                .orElse(Color.GRAY);

        // URL del icono de perfil usando el CDN oficial de Data Dragon de Riot
        String profileIconUrl = String.format(
            "https://ddragon.leagueoflegends.com/cdn/%s/img/profileicon/%d.png",
            DDRAGON_VERSION, summoner.getProfileIconId()
        );

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 " + account.getGameName() + "  #" + account.getTagLine())
                .setColor(embedColor)
                .setThumbnail(profileIconUrl)
                // Fila de datos generales del jugador
                .addField("🌍 Región",  region.toUpperCase(),                  true)
                .addField("📈 Nivel",   String.valueOf(summoner.getSummonerLevel()), true)
                .addBlankField(true) // columna vacía para alinear la fila en 3 columnas
                // Separador visual
                .addField("​", "​", false); // campo en blanco como separador

        // Sección Solo/Duo Ranked
        soloEntry.ifPresentOrElse(
            entry -> embed.addField("⚔️  Solo / Duo", buildRankSection(entry), true),
            ()    -> embed.addField("⚔️  Solo / Duo", "_Sin clasificar_",      true)
        );

        // Sección Flex Ranked
        flexEntry.ifPresentOrElse(
            entry -> embed.addField("👥  Flex 5v5", buildRankSection(entry), true),
            ()    -> embed.addField("👥  Flex 5v5", "_Sin clasificar_",      true)
        );

        embed.setFooter("Datos de Riot Games API  •  " + region.toUpperCase(), null)
             .setTimestamp(Instant.now());

        return embed.build();
    }

    /**
     * Construye el texto de contenido para un campo de rango en el embed.
     * Incluye tier, LP, ratio de victorias e indicadores especiales.
     */
    private static String buildRankSection(LeagueEntryDto entry) {
        StringBuilder sb = new StringBuilder();

        // Línea 1: Tier + División + LP
        sb.append("**").append(entry.getFormattedRank()).append("**\n");

        // Línea 2: Victorias / Derrotas / WinRate
        sb.append(entry.getWins()).append("V  /  ")
          .append(entry.getLosses()).append("D")
          .append("  —  **").append(entry.getWinRate()).append("% WR**");

        // Indicadores especiales de estado de la cuenta
        if (entry.isHotStreak())  sb.append("\n🔥 _Racha ganadora_");
        if (entry.isFreshBlood()) sb.append("\n🆕 _Recién ascendido_");
        if (entry.isVeteran())    sb.append("\n🏆 _Veterano_");

        return sb.toString();
    }

    /**
     * Crea un embed de error con formato visual consistente.
     *
     * @param title   Título corto del error
     * @param message Descripción detallada del problema
     */
    public static MessageEmbed buildErrorEmbed(String title, String message) {
        return new EmbedBuilder()
                .setTitle("❌  " + title)
                .setDescription(message)
                .setColor(Color.RED)
                .setFooter("LoL Stats Bot", null)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Construye el embed con el historial de las últimas N partidas del jugador.
     */
    public static MessageEmbed buildMatchHistoryEmbed(
            AccountDto account,
            List<MatchDto> matches,
            String puuid,
            String platform) {

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 Últimas " + matches.size() + " partidas  —  "
                          + account.getGameName() + "  #" + account.getTagLine())
                .setColor(new Color(30, 100, 200))
                .setFooter("Datos de Riot Games API  •  " + platform.toUpperCase(), null)
                .setTimestamp(Instant.now());

        for (MatchDto match : matches) {
            MatchDto.Info info = match.getInfo();

            MatchParticipantDto p = info.getParticipants().stream()
                    .filter(part -> puuid.equals(part.getPuuid()))
                    .findFirst()
                    .orElse(null);

            if (p == null) continue;

            String fieldName = String.format("%s  **%s**  ·  %s  ·  %s",
                    p.isWin() ? "✅" : "❌",
                    p.getChampionName(),
                    getQueueName(info.getQueueId()),
                    formatDuration(info.getGameDuration()));

            int cs = p.getTotalCS();
            String fieldValue = cs > 0
                    ? String.format("`%s`  KDA %s  ·  %d CS  ·  %s",
                            p.getFormattedKda(), p.getKdaRatio(), cs, timeAgo(info.getGameCreation()))
                    : String.format("`%s`  KDA %s  ·  %s",
                            p.getFormattedKda(), p.getKdaRatio(), timeAgo(info.getGameCreation()));

            embed.addField(fieldName, fieldValue, false);
        }

        return embed.build();
    }

    private static String getQueueName(int queueId) {
        return switch (queueId) {
            case 420  -> "Ranked Solo";
            case 440  -> "Ranked Flex";
            case 400  -> "Normal Draft";
            case 430  -> "Normal Blind";
            case 450  -> "ARAM";
            case 490  -> "Quickplay";
            case 700  -> "Clash";
            case 900, 1900 -> "URF";
            case 1020 -> "One for All";
            case 1300 -> "Nexus Blitz";
            case 1700 -> "Arena";
            default   -> "Partida";
        };
    }

    private static String formatDuration(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String timeAgo(long gameCreationMs) {
        long diffMin = (System.currentTimeMillis() - gameCreationMs) / 60_000;
        if (diffMin < 60)   return "hace " + diffMin + "m";
        if (diffMin < 1440) return "hace " + (diffMin / 60) + "h";
        long days = diffMin / 1440;
        if (days < 7)       return "hace " + days + "d";
        return "hace " + (days / 7) + " sem";
    }

    /** Mapea cada tier de clasificación a su color representativo. */
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
