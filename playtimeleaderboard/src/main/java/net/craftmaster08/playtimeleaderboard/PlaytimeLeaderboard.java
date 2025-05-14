package net.craftmaster08.playtimeleaderboard;

import net.craftmaster08.cm08statscore.StatsCore;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class for the PlaytimeLeaderboard mod, handling initialization and server setup.
 */
@Mod(PlaytimeLeaderboard.MODID)
public class PlaytimeLeaderboard {
    public static final String MODID = "playtimeleaderboard";
    private static final Logger LOGGER = LogManager.getLogger(PlaytimeLeaderboard.class);
    private static MinecraftServer server;
    private boolean commandsRegistered = false;

    public PlaytimeLeaderboard() {
        LOGGER.info("Starting PlaytimeLeaderboard constructor");
        if (!isStatsCorePresent()) {
            LOGGER.error("StatsCore mod (cm08statscore) is required but not detected. Disabling PlaytimeLeaderboard.");
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onServerStarting);
        // Remove RegisterCommandsEvent listener to avoid early registration
        LOGGER.info("Initialized PlaytimeLeaderboard mod");
    }

    /**
     * Checks if the StatsCore mod is loaded.
     *
     * @return True if StatsCore is present, false otherwise.
     */
    private boolean isStatsCorePresent() {
        boolean present = net.minecraftforge.fml.ModList.get().isLoaded(StatsCore.MODID);
        LOGGER.info("StatsCore present: {}", present);
        return present;
    }

    /**
     * Initializes the server instance and registers commands when dependencies are ready.
     *
     * @param event The server starting event.
     */
    private void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        LOGGER.info("PlaytimeLeaderboard server set");

        if (!commandsRegistered && areDependenciesReady()) {
            registerCommands(event.getServer().getCommands().getDispatcher());
            commandsRegistered = true;
            LOGGER.info("Registered /playtime command during server starting");
        } else if (!areDependenciesReady()) {
            LOGGER.warn("Cannot register /playtime command: Dependencies not fully initialized (ConfigManager: {}, DailyPlaytimeTracker: {})",
                    StatsCore.getConfigManager() != null ? "present" : "null",
                    StatsCore.getDailyPlaytimeTracker() != null ? "present" : "null");
        } else {
            LOGGER.info("Skipping /playtime command registration; already registered");
        }
    }

    /**
     * Checks if all required dependencies are initialized.
     *
     * @return True if ConfigManager and DailyPlaytimeTracker are ready, false otherwise.
     */
    private boolean areDependenciesReady() {
        return StatsCore.getConfigManager() != null &&
                StatsCore.getDailyPlaytimeTracker() != null &&
                server != null;
    }

    /**
     * Registers the /playtime command.
     *
     * @param dispatcher The command dispatcher to register with.
     */
    private void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        PlaytimeRunCommand.register(dispatcher);
    }

    /**
     * Gets the current Minecraft server instance.
     *
     * @return The server instance, or null if not initialized.
     */
    public static MinecraftServer getServer() {
        if (server == null) {
            LOGGER.warn("Attempted to access server before initialization");
        }
        return server;
    }
}