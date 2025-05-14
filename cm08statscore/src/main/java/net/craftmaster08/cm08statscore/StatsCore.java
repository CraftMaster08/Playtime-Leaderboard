package net.craftmaster08.cm08statscore;

import net.craftmaster08.cm08statscore.cache.PlaytimeUsernameCache;
import net.craftmaster08.cm08statscore.config.ConfigManager;
import net.craftmaster08.cm08statscore.playtime.DailyPlaytimeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class for the StatsCore mod, managing core functionality and dependencies.
 */
@Mod(StatsCore.MODID)
public class StatsCore {
    public static final String MODID = "cm08statscore";
    private static final Logger LOGGER = LogManager.getLogger(StatsCore.class);
    private static ConfigManager configManager;
    private static PlaytimeUsernameCache usernameCache;
    private static DailyPlaytimeTracker dailyPlaytimeTracker;
    private static MinecraftServer server;

    public StatsCore() {
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("Initialized StatsCore mod");
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        CommandRegistry.register(event.getDispatcher());
        LOGGER.info("Registered StatsCore commands");
    }

    /**
     * Handles server and player events for StatsCore.
     */
    private static class EventHandler {
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onServerStarting(ServerStartingEvent event) {
            server = event.getServer();
            ServiceInitializer.initialize(server);
            LOGGER.info("StatsCore server dependencies initialized with {} username colors and {} blacklisted players",
                    configManager.getUsernameColors().size(), configManager.getBlacklistedPlayers().size());
        }

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
                if (player.tickCount % 100 == 0 && dailyPlaytimeTracker != null) {
                    dailyPlaytimeTracker.updatePlayer(player);
                }
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                if (dailyPlaytimeTracker != null) {
                    dailyPlaytimeTracker.playerLoggedIn(player);
                }
                if (usernameCache != null) {
                    usernameCache.storeUsername(player.getUUID(), player.getGameProfile().getName());
                }
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && dailyPlaytimeTracker != null) {
                dailyPlaytimeTracker.playerLoggedOut(player);
            }
        }
    }

    /**
     * Initializes StatsCore services during server startup.
     */
    private static class ServiceInitializer {
        static void initialize(MinecraftServer server) {
            try {
                dailyPlaytimeTracker = new DailyPlaytimeTracker(server);
                LOGGER.info("DailyPlaytimeTracker initialized successfully");
            } catch (RuntimeException e) {
                LOGGER.error("Failed to initialize DailyPlaytimeTracker: {}", e.getMessage(), e);
                dailyPlaytimeTracker = null;
            }
            configManager = new ConfigManager(dailyPlaytimeTracker);
            usernameCache = PlaytimeUsernameCache.getInstance(server);
            try {
                configManager.loadConfig();
            } catch (Exception e) {
                LOGGER.error("Failed to load config: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Gets the configuration manager instance.
     *
     * @return The ConfigManager, or null if not initialized.
     */
    public static ConfigManager getConfigManager() {
        if (configManager == null) {
            LOGGER.warn("ConfigManager accessed before initialization");
        }
        return configManager;
    }

    /**
     * Gets the daily playtime tracker instance.
     *
     * @return The DailyPlaytimeTracker, or null if not initialized.
     */
    public static DailyPlaytimeTracker getDailyPlaytimeTracker() {
        if (dailyPlaytimeTracker == null) {
            LOGGER.warn("DailyPlaytimeTracker accessed but is null");
        }
        return dailyPlaytimeTracker;
    }
}