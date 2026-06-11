package com.lolbot.listeners;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires when the bot successfully establishes a Gateway session with Discord.
 * JDA calls onReady() exactly once after the session is fully initialized.
 */
public class ReadyListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    @Override
    public void onReady(ReadyEvent event) {
        String botName    = event.getJDA().getSelfUser().getName();
        int    guildCount = event.getGuildTotalCount();

        logger.info("================================================");
        logger.info("  Bot ready: {}", botName);
        logger.info("  Connected servers: {}", guildCount);
        logger.info("================================================");
    }
}
