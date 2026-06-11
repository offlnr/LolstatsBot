package com.lolbot.listeners;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener que reacciona al evento de conexión exitosa del bot con Discord.
 * JDA llama a onReady() una vez que la sesión Gateway está completamente establecida.
 */
public class ReadyListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    @Override
    public void onReady(ReadyEvent event) {
        String botName   = event.getJDA().getSelfUser().getName();
        int    guildCount = event.getGuildTotalCount();

        logger.info("================================================");
        logger.info("  Bot listo: {}", botName);
        logger.info("  Servidores conectados: {}", guildCount);
        logger.info("================================================");
    }
}
