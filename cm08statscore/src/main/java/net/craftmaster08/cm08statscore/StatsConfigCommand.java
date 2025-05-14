package net.craftmaster08.cm08statscore;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.craftmaster08.cm08statscore.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registers and handles the /statsconfig command and its subcommands for managing StatsCore configuration.
 */
public class StatsConfigCommand {
    private static final Logger LOGGER = LogManager.getLogger(StatsConfigCommand.class);

    private static final List<String> MINECRAFT_COLORS = Arrays.stream(ChatFormatting.values())
            .filter(ChatFormatting::isColor)
            .map(color -> color.getName().toUpperCase())
            .collect(Collectors.toList());

    private static final List<String> TIMEZONES = Arrays.asList(
            "UTC-12:00", "UTC-11:00", "UTC-10:00", "UTC-09:00", "UTC-08:00",
            "UTC-07:00", "UTC-06:00", "UTC-05:00", "UTC-04:00", "UTC-03:00",
            "UTC-02:00", "UTC-01:00", "UTC+00:00", "UTC+01:00", "UTC+02:00",
            "UTC+03:00", "UTC+04:00", "UTC+05:00", "UTC+06:00", "UTC+07:00",
            "UTC+08:00", "UTC+09:00", "UTC+10:00", "UTC+11:00", "UTC+12:00"
    );

    /**
     * Registers the /statsconfig command and its subcommands.
     *
     * @param dispatcher The command dispatcher to register the command with.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("statsconfig")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(StatsConfigCommand::reloadConfig))
                .then(Commands.literal("blacklist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(onlinePlayers())
                                        .executes(context -> blacklistAdd(context, StringArgumentType.getString(context, "player")))))
                        .then(Commands.literal("list")
                                .executes(StatsConfigCommand::blacklistList))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(blacklistedPlayers())
                                        .executes(context -> blacklistRemove(context, StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("color")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(onlinePlayers())
                                .executes(context -> colorShow(context, StringArgumentType.getString(context, "player")))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(MINECRAFT_COLORS, builder))
                                                .executes(context -> colorSet(context, StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "color")))))
                                .then(Commands.literal("reset")
                                        .executes(context -> colorReset(context, StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("dailyresettime")
                        .executes(StatsConfigCommand::dailyResetTimeShow)
                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        TIMEZONES.stream().map(tz -> "00:00:00 " + tz), builder))
                                .executes(context -> dailyResetTimeSet(context, StringArgumentType.getString(context, "time")))));

        try {
            dispatcher.register(command);
            LOGGER.info("Successfully registered /statsconfig command");
        } catch (Exception e) {
            LOGGER.error("Failed to register /statsconfig command", e);
        }
    }

    /**
     * Suggestion provider for online players.
     */
    private static SuggestionProvider<CommandSourceStack> onlinePlayers() {
        return (context, builder) -> {
            MinecraftServer server = context.getSource().getServer();
            if (server != null) {
                return SharedSuggestionProvider.suggest(
                        server.getPlayerList().getPlayers().stream()
                                .map(player -> player.getGameProfile().getName()),
                        builder
                );
            }
            return builder.buildFuture();
        };
    }

    /**
     * Suggestion provider for blacklisted players.
     */
    private static SuggestionProvider<CommandSourceStack> blacklistedPlayers() {
        return (context, builder) -> {
            ConfigManager config = StatsCore.getConfigManager();
            if (config != null) {
                return SharedSuggestionProvider.suggest(config.getBlacklistedPlayers(), builder);
            }
            return builder.buildFuture();
        };
    }

    /**
     * Reloads the StatsCore configuration file.
     */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager configManager = StatsCore.getConfigManager();
            if (configManager == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            configManager.loadConfig();
            source.sendSystemMessage(Component.literal("Successfully reloaded statscore_config.json")
                    .withStyle(ChatFormatting.GREEN));
            LOGGER.info("Configuration reloaded by {}", source.getTextName());
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to reload statscore_config.json: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to reload configuration", e);
            return 0;
        }
    }

    /**
     * Adds a player to the blacklist and saves the config.
     */
    private static int blacklistAdd(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            Set<String> blacklistedPlayers = new HashSet<>(config.getBlacklistedPlayers());
            String playerLower = player.toLowerCase();
            if (blacklistedPlayers.add(playerLower)) {
                config.blacklistedPlayers = Set.copyOf(blacklistedPlayers);
                saveConfig(config);
                source.sendSystemMessage(Component.literal("Added " + player + " to blacklist")
                        .withStyle(ChatFormatting.GREEN));
                LOGGER.info("{} added {} to blacklist", source.getTextName(), player);
                return 1;
            } else {
                source.sendSystemMessage(Component.literal(player + " is already blacklisted")
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to add " + player + " to blacklist: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to blacklist add {}", player, e);
            return 0;
        }
    }

    /**
     * Lists all blacklisted players.
     */
    private static int blacklistList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            Set<String> blacklistedPlayers = config.getBlacklistedPlayers();
            if (blacklistedPlayers.isEmpty()) {
                source.sendSystemMessage(Component.literal("Blacklist is empty")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                String players = String.join(", ", blacklistedPlayers);
                source.sendSystemMessage(Component.literal("Blacklisted players: " + players)
                        .withStyle(ChatFormatting.WHITE));
            }
            LOGGER.info("{} listed blacklist", source.getTextName());
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to list blacklist: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to list blacklist", e);
            return 0;
        }
    }

    /**
     * Removes a player from the blacklist and saves the config.
     */
    private static int blacklistRemove(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            Set<String> blacklistedPlayers = new HashSet<>(config.getBlacklistedPlayers());
            String playerLower = player.toLowerCase();
            if (blacklistedPlayers.remove(playerLower)) {
                config.blacklistedPlayers = Set.copyOf(blacklistedPlayers);
                saveConfig(config);
                source.sendSystemMessage(Component.literal("Removed " + player + " from blacklist")
                        .withStyle(ChatFormatting.GREEN));
                LOGGER.info("{} removed {} from blacklist", source.getTextName(), player);
                return 1;
            } else {
                source.sendSystemMessage(Component.literal(player + " is not blacklisted")
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to remove " + player + " from blacklist: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to remove {} from blacklist", player, e);
            return 0;
        }
    }

    /**
     * Shows the color of a player.
     */
    private static int colorShow(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            ChatFormatting color = config.getUsernameColors().getOrDefault(player.toLowerCase(), ChatFormatting.WHITE);
            source.sendSystemMessage(Component.literal(player + "'s color: " + color.getName().toUpperCase())
                    .withStyle(color));
            LOGGER.info("{} viewed color for {}", source.getTextName(), player);
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to show color for " + player + ": " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to show color for {}", player, e);
            return 0;
        }
    }

    /**
     * Sets a player's color and saves the config.
     */
    private static int colorSet(CommandContext<CommandSourceStack> context, String player, String colorName) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            ChatFormatting color = ChatFormatting.getByName(colorName.toUpperCase());
            if (color == null || !color.isColor()) {
                source.sendSystemMessage(Component.literal("Invalid color: " + colorName)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            Map<String, ChatFormatting> usernameColors = new HashMap<>(config.getUsernameColors());
            usernameColors.put(player.toLowerCase(), color);
            config.usernameColors = Map.copyOf(usernameColors);
            saveConfig(config);
            source.sendSystemMessage(Component.literal("Set " + player + "'s color to " + color.getName().toUpperCase())
                    .withStyle(color));
            LOGGER.info("{} set {}'s color to {}", source.getTextName(), player, color.getName());
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to set color for " + player + ": " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to set color for {} to {}", player, colorName, e);
            return 0;
        }
    }

    /**
     * Resets a player's color to white and saves the config.
     */
    private static int colorReset(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            Map<String, ChatFormatting> usernameColors = new HashMap<>(config.getUsernameColors());
            if (usernameColors.remove(player.toLowerCase()) != null) {
                config.usernameColors = Map.copyOf(usernameColors);
                saveConfig(config);
                source.sendSystemMessage(Component.literal("Reset " + player + "'s color to WHITE")
                        .withStyle(ChatFormatting.WHITE));
                LOGGER.info("{} reset {}'s color", source.getTextName(), player);
                return 1;
            } else {
                source.sendSystemMessage(Component.literal(player + "'s color is already default")
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to reset color for " + player + ": " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to reset color for {}", player, e);
            return 0;
        }
    }

    /**
     * Shows the daily reset time.
     */
    private static int dailyResetTimeShow(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            source.sendSystemMessage(Component.literal("Daily reset time: " + config.dailyResetTime)
                    .withStyle(ChatFormatting.WHITE));
            LOGGER.info("{} viewed daily reset time", source.getTextName());
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to show daily reset time: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to show daily reset time", e);
            return 0;
        }
    }

    /**
     * Sets the daily reset time and saves the config.
     */
    private static int dailyResetTimeSet(CommandContext<CommandSourceStack> context, String time) {
        CommandSourceStack source = context.getSource();
        try {
            ConfigManager config = StatsCore.getConfigManager();
            if (config == null) {
                throw new IllegalStateException("ConfigManager not initialized");
            }
            // Validate time format (HH:mm:ss UTC±HH:MM)
            String[] parts = time.split(" ");
            if (parts.length != 2 || !parts[0].matches("\\d{2}:\\d{2}:\\d{2}") || !parts[1].matches("UTC[+-]\\d{2}:\\d{2}")) {
                source.sendSystemMessage(Component.literal("Invalid time format. Use HH:mm:ss UTC±HH:MM")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            config.dailyResetTime = time;
            if (config.dailyPlaytimeTracker != null) {
                config.dailyPlaytimeTracker.setDailyResetTime(time);
            }
            saveConfig(config);
            source.sendSystemMessage(Component.literal("Set daily reset time to " + time)
                    .withStyle(ChatFormatting.GREEN));
            LOGGER.info("{} set daily reset time to {}", source.getTextName(), time);
            return 1;
        } catch (Exception e) {
            source.sendSystemMessage(Component.literal("Failed to set daily reset time: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("Failed to set daily reset time to {}", time, e);
            return 0;
        }
    }

    /**
     * Saves the configuration to disk.
     */
    private static void saveConfig(ConfigManager config) {
        JsonObject configJson = new JsonObject();
        configJson.addProperty("_comment", "DO NOT EDIT THIS FILE MANUALLY. Use /statsconfig commands to modify settings.");
        configJson.addProperty("daily_reset_time", config.dailyResetTime);

        JsonObject usernameColorsJson = new JsonObject();
        config.getUsernameColors().forEach((username, color) ->
                usernameColorsJson.addProperty(username, color.getName().toUpperCase()));
        configJson.add("username_colors", usernameColorsJson);

        com.google.gson.JsonArray blacklistedPlayersJson = new com.google.gson.JsonArray();
        config.getBlacklistedPlayers().forEach(blacklistedPlayersJson::add);
        configJson.add("blacklisted_players", blacklistedPlayersJson);

        try (java.io.FileWriter writer = new java.io.FileWriter(ConfigManager.CONFIG_PATH.toFile())) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(configJson, writer);
            LOGGER.info("Saved statscore_config.json");
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to save statscore_config.json", e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }
}