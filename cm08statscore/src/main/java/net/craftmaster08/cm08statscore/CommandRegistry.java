package net.craftmaster08.cm08statscore;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralizes command registration for the StatsCore mod.
 */
public class CommandRegistry {
    private static final Logger LOGGER = LogManager.getLogger(CommandRegistry.class);

    /**
     * Registers all StatsCore commands with the command dispatcher.
     *
     * @param dispatcher The command dispatcher to register commands with.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        StatsConfigCommand.register(dispatcher);
        LOGGER.info("Registered /statsconfig command");
    }
}