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
 * Punto de entrada del LoL Stats Bot.
 *
 * Responsabilidades de esta clase:
 *  1. Cargar la configuración (tokens y API keys desde config.properties o env vars).
 *  2. Construir la instancia de JDA con los Intents y Cachés óptimos.
 *  3. Registrar los listeners de eventos (ReadyListener, CommandManager).
 *  4. Esperar a que JDA esté listo (awaitReady) y registrar los Slash Commands.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *  NOTA SOBRE LOS GATEWAY INTENTS:
 *  Los Intents declaran qué eventos quieres recibir de Discord. Hay intents
 *  "privilegiados" que requieren habilitación manual en el Developer Portal:
 *   - GUILD_MEMBERS      → lista de miembros (no la necesitamos)
 *   - MESSAGE_CONTENT    → contenido de mensajes (no la necesitamos; usamos slash commands)
 *   - GUILD_PRESENCES    → estado online/offline (no la necesitamos)
 *
 *  Para un bot de slash commands usamos createLight() que solo incluye los
 *  intents mínimos necesarios para el funcionamiento básico.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Iniciando LoL Stats Bot...");

        // Carga la configuración. Lanza IllegalStateException si falta DISCORD_TOKEN o RIOT_API_KEY.
        BotConfig config = BotConfig.load();

        try {
            JDA jda = JDABuilder
                    // createLight incluye solo los intents básicos: GUILDS y GUILD_MESSAGES (sin contenido).
                    // Es suficiente para bots que usan exclusivamente slash commands.
                    .createLight(config.getDiscordToken())

                    // Aunque createLight ya minimiza los intents, declaramos explícitamente
                    // que no necesitamos ninguno adicional para ser claros sobre el alcance del bot.
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)

                    // Deshabilitamos cachés internas de JDA que no usamos para reducir memoria.
                    // Un bot de estadísticas no necesita cachear estados de voz, emojis, etc.
                    .disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.SCHEDULED_EVENTS,
                            CacheFlag.MEMBER_OVERRIDES
                    )

                    // Registramos los listeners ANTES de build() para no perder eventos
                    // que puedan ocurrir durante la inicialización.
                    .addEventListeners(
                            new ReadyListener(),       // loguea cuando el bot se conecta
                            new CommandManager(config) // maneja el slash command /stats
                    )
                    .build();

            // awaitReady() bloquea el hilo principal hasta que JDA haya terminado
            // de conectarse al Gateway de Discord y cargar los datos iniciales.
            // Solo después de esto podemos registrar comandos de forma segura.
            jda.awaitReady();

            // Registramos los Slash Commands globales.
            //
            // DESARROLLO vs PRODUCCIÓN:
            //  - Comandos de servidor (guild): instantáneos, útiles para testear.
            //    Cambiar por: jda.getGuildById("TU_GUILD_ID").updateCommands()...
            //  - Comandos globales: tardan hasta 1 hora en propagarse a todos los servidores.
            //    Usar en producción.
            registerSlashCommands(jda);

        } catch (InterruptedException e) {
            logger.error("El hilo principal fue interrumpido durante la inicialización de JDA.", e);
            Thread.currentThread().interrupt(); // Buena práctica: restaurar el flag de interrupción
            System.exit(1);
        }
    }

    /**
     * Registra todos los Slash Commands del bot en Discord.
     * Se hace DESPUÉS de awaitReady() para garantizar que JDA esté autenticado.
     */
    private static void registerSlashCommands(JDA jda) {
        jda.updateCommands()
            .addCommands(

                Commands.slash("stats", "Muestra el rango y estadísticas de un invocador de League of Legends")
                    .addOption(OptionType.STRING, "invocador",
                        "Riot ID completo del jugador (ej: Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Servidor del jugador (ej: na1, euw1, kr, la1, br1). Por defecto: la1", false),

                Commands.slash("matches", "Muestra el historial de las últimas 10 partidas de un jugador")
                    .addOption(OptionType.STRING, "invocador",
                        "Riot ID completo del jugador (ej: Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Servidor del jugador (ej: na1, euw1, kr, la1, br1). Por defecto: la1", false),

                Commands.slash("partidas", "Muestra el historial de las últimas 10 partidas de un jugador")
                    .addOption(OptionType.STRING, "invocador",
                        "Riot ID completo del jugador (ej: Faker#KR1)", true)
                    .addOption(OptionType.STRING, "region",
                        "Servidor del jugador (ej: na1, euw1, kr, la1, br1). Por defecto: la1", false)

            )
            .queue(
                success -> logger.info("Slash commands registrados: {} comando(s).", success.size()),
                failure -> logger.error("No se pudieron registrar los slash commands: {}", failure.getMessage())
            );
    }
}
