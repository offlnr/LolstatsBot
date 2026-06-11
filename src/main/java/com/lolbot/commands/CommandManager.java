package com.lolbot.commands;

import com.lolbot.config.BotConfig;
import com.lolbot.models.AccountDto;
import com.lolbot.models.LeagueEntryDto;
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
 * Gestor central de Slash Commands del bot.
 *
 * Extiende {@link ListenerAdapter} para recibir eventos de Discord a través de JDA.
 *
 * ┌────────────────────────────────────────────────────────────────────┐
 * │  Por qué usamos deferReply() + ExecutorService:                    │
 * │                                                                    │
 * │  Discord exige una respuesta inicial en < 3 segundos.             │
 * │  La cadena de peticiones a Riot API toma 1-3 segundos.            │
 * │  deferReply() envía inmediatamente el indicador "pensando..."     │
 * │  y nos da 15 minutos para editar la respuesta con datos reales.   │
 * │                                                                    │
 * │  Las peticiones HTTP las ejecutamos en un hilo separado para      │
 * │  no bloquear el hilo de eventos de JDA (que es compartido         │
 * │  para todos los eventos de todos los servidores).                 │
 * └────────────────────────────────────────────────────────────────────┘
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final RiotApiService riotApiService;
    private final BotConfig      config;

    // Pool de hilos virtuales (Java 21). Ideal para tareas I/O-bound como peticiones HTTP.
    // Alternativa Java 17: Executors.newCachedThreadPool()
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CommandManager(BotConfig config) {
        this.config          = config;
        this.riotApiService  = new RiotApiService(config.getRiotApiKey());
    }

    // =========================================================================
    // Listener principal — recibe TODOS los slash commands del bot
    // =========================================================================

    /**
     * JDA invoca este método cada vez que un usuario ejecuta un slash command.
     * IMPORTANTE: Este método corre en el hilo de eventos de JDA — debe ser NO bloqueante.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        String commandName = event.getName();
        if (!commandName.equals("stats") && !commandName.equals("matches") && !commandName.equals("partidas")) return;

        event.deferReply().queue();

        String riotIdInput = event.getOption("invocador").getAsString().trim();
        String regionInput = event.getOption("region") != null
                ? event.getOption("region").getAsString()
                : config.getDefaultRegion();
        String platform = RegionUtil.normalizePlatform(regionInput);

        logger.info("/{} invocado por '{}' → invocador='{}', plataforma='{}'",
                commandName, event.getUser().getName(), riotIdInput, platform);

        if (commandName.equals("stats")) {
            executor.submit(() -> processStatsCommand(event, riotIdInput, platform));
        } else {
            executor.submit(() -> processMatchesCommand(event, riotIdInput, platform));
        }
    }

    // =========================================================================
    // Procesamiento en hilo separado — aquí se hacen las peticiones a Riot API
    // =========================================================================

    /**
     * Ejecuta el flujo completo de consulta a Riot API y edita la respuesta diferida.
     *
     * Este método corre en un hilo del pool, NO en el hilo de JDA.
     * Después de deferReply(), usamos event.getHook() para editar la respuesta.
     */
    private void processStatsCommand(SlashCommandInteractionEvent event,
                                     String riotIdInput,
                                     String platform) {
        try {
            // ── Validación del formato del Riot ID ──
            // El formato correcto es "NombreDeJugador#TAG", ej: "Faker#KR1"
            if (!riotIdInput.contains("#")) {
                sendError(event,
                    "Formato inválido",
                    "El Riot ID debe tener el formato `Nombre#TAG`\n" +
                    "Ejemplo: `/stats invocador:Faker#KR1`"
                );
                return;
            }

            // Separamos en máximo 2 partes por si el nombre contiene '#' (raro pero posible)
            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Formato inválido", "El nombre o el tag no pueden estar vacíos.");
                return;
            }

            // ── CONSULTA 1: Account-V1 → PUUID ──
            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);

            // ── CONSULTA 2: Summoner-V4 → nivel e ID del invocador ──
            SummonerDto summoner = riotApiService.getSummonerByPuuid(account.getPuuid(), platform);

            // ── CONSULTA 3: League-V4 → entradas de clasificación ──
            List<LeagueEntryDto> entries = riotApiService.getLeagueEntries(
                account.getPuuid(), platform
            );

            // ── Construimos y enviamos el embed con toda la información ──
            MessageEmbed statsEmbed = StatsEmbedBuilder.buildStatsEmbed(
                account, summoner, entries, platform
            );

            // editOriginal() reemplaza el "pensando..." inicial con el embed real.
            // .queue() hace el envío de forma asíncrona dentro del propio hilo de JDA.
            event.getHook().editOriginalEmbeds(statsEmbed).queue();

            logger.info("Respuesta enviada correctamente para '{}'", riotIdInput);

        } catch (RiotApiException e) {
            // Error controlado de la API (jugador no encontrado, key inválida, etc.)
            logger.warn("RiotApiException para '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Error de la API de Riot", e.getMessage());

        } catch (IOException e) {
            // Error de red: timeout, DNS, conexión rechazada
            logger.error("IOException consultando la API para '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Error de conexión",
                "No se pudo contactar con los servidores de Riot Games. Intenta de nuevo en unos momentos.");

        } catch (Exception e) {
            // Captura general para errores inesperados — nunca dejamos la respuesta en "pensando..."
            logger.error("Error inesperado en /stats para '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Error inesperado",
                "Ocurrió un error interno. Si el problema persiste, contacta al administrador del bot.");
        }
    }

    private void processMatchesCommand(SlashCommandInteractionEvent event,
                                       String riotIdInput,
                                       String platform) {
        try {
            if (!riotIdInput.contains("#")) {
                sendError(event, "Formato inválido",
                    "El Riot ID debe tener el formato `Nombre#TAG`\n" +
                    "Ejemplo: `/matches invocador:Faker#KR1`");
                return;
            }

            String[] parts    = riotIdInput.split("#", 2);
            String   gameName = parts[0].trim();
            String   tagLine  = parts[1].trim();

            if (gameName.isEmpty() || tagLine.isEmpty()) {
                sendError(event, "Formato inválido", "El nombre o el tag no pueden estar vacíos.");
                return;
            }

            AccountDto account = riotApiService.getAccountByRiotId(gameName, tagLine, platform);

            List<String> matchIds = riotApiService.getMatchIds(account.getPuuid(), platform, 10);

            if (matchIds.isEmpty()) {
                sendError(event, "Sin partidas", "No se encontraron partidas recientes para este jugador.");
                return;
            }

            List<MatchDto> matches = new ArrayList<>();
            for (String matchId : matchIds) {
                matches.add(riotApiService.getMatch(matchId, platform));
            }

            event.getHook().editOriginalEmbeds(
                StatsEmbedBuilder.buildMatchHistoryEmbed(account, matches, account.getPuuid(), platform)
            ).queue();

            logger.info("Historial de partidas enviado correctamente para '{}'", riotIdInput);

        } catch (RiotApiException e) {
            logger.warn("RiotApiException en /matches para '{}': {} [HTTP {}]",
                riotIdInput, e.getMessage(), e.getHttpCode());
            sendError(event, "Error de la API de Riot", e.getMessage());
        } catch (IOException e) {
            logger.error("IOException en /matches para '{}': {}", riotIdInput, e.getMessage());
            sendError(event, "Error de conexión",
                "No se pudo contactar con los servidores de Riot Games. Intenta de nuevo en unos momentos.");
        } catch (Exception e) {
            logger.error("Error inesperado en /matches para '{}': {}", riotIdInput, e.getMessage(), e);
            sendError(event, "Error inesperado",
                "Ocurrió un error interno. Si el problema persiste, contacta al administrador del bot.");
        }
    }

    /** Método auxiliar para enviar un embed de error de forma concisa. */
    private void sendError(SlashCommandInteractionEvent event, String title, String description) {
        event.getHook()
             .editOriginalEmbeds(StatsEmbedBuilder.buildErrorEmbed(title, description))
             .queue();
    }
}
