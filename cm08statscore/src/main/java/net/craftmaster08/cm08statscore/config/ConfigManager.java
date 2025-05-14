package net.craftmaster08.cm08statscore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.craftmaster08.cm08statscore.playtime.DailyPlaytimeTracker;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages the StatsCore configuration, including username colors and blacklisted players.
 */
public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger(ConfigManager.class);
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("statscore_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, ChatFormatting> usernameColors;
    public Set<String> blacklistedPlayers;
    public String dailyResetTime;
    public final DailyPlaytimeTracker dailyPlaytimeTracker;

    public ConfigManager(DailyPlaytimeTracker dailyPlaytimeTracker) {
        this.dailyPlaytimeTracker = dailyPlaytimeTracker;
        if (dailyPlaytimeTracker == null) {
            LOGGER.warn("DailyPlaytimeTracker is null; daily playtime features will be disabled");
        }
        this.usernameColors = Map.of();
        this.blacklistedPlayers = Set.of();
        this.dailyResetTime = "00:00:00 UTC";
        loadConfig();
    }

    /**
     * Gets the map of username colors.
     *
     * @return An immutable map of usernames to their colors.
     */
    public Map<String, ChatFormatting> getUsernameColors() {
        return usernameColors;
    }

    /**
     * Gets the set of blacklisted players.
     *
     * @return An immutable set of blacklisted player names.
     */
    public Set<String> getBlacklistedPlayers() {
        return blacklistedPlayers;
    }

    /**
     * Loads the configuration from the JSON file.
     */
    public void loadConfig() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            ConfigLoader.createDefaultConfig(configFile);
        }

        ConfigLoader.load(configFile, this);
        if (dailyPlaytimeTracker != null) {
            dailyPlaytimeTracker.setDailyResetTime(dailyResetTime);
        } else {
            LOGGER.warn("Skipping dailyPlaytimeTracker.setDailyResetTime due to null tracker");
        }
    }

    /**
     * Handles configuration loading and default creation.
     */
    private static class ConfigLoader {
        static void load(File configFile, ConfigManager manager) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject configJson = GSON.fromJson(reader, JsonObject.class);
                if (configJson == null) {
                    throw new JsonParseException("Config file is empty or invalid JSON");
                }

                Map<String, ChatFormatting> tempUsernameColors = new HashMap<>();
                if (configJson.has("username_colors")) {
                    JsonObject usernameColorsJson = configJson.getAsJsonObject("username_colors");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : usernameColorsJson.entrySet()) {
                        String username = entry.getKey().toLowerCase();
                        String colorName = entry.getValue().getAsString().toUpperCase();
                        ChatFormatting color = ChatFormatting.getByName(colorName);
                        if (color == null || !color.isColor()) {
                            LOGGER.warn("Invalid Minecraft color for {}: {}", username, colorName);
                            continue;
                        }
                        tempUsernameColors.put(username, color);
                    }
                }

                Set<String> tempBlacklistedPlayers = new HashSet<>();
                if (configJson.has("blacklisted_players")) {
                    configJson.getAsJsonArray("blacklisted_players")
                            .forEach(element -> tempBlacklistedPlayers.add(element.getAsString().toLowerCase()));
                }

                String tempDailyResetTime = configJson.has("daily_reset_time")
                        ? configJson.get("daily_reset_time").getAsString()
                        : "00:00:00 UTC";

                manager.usernameColors = Map.copyOf(tempUsernameColors);
                manager.blacklistedPlayers = Set.copyOf(tempBlacklistedPlayers);
                manager.dailyResetTime = tempDailyResetTime;
                LOGGER.info("Successfully loaded statscore_config.json");
            } catch (IOException | JsonParseException e) {
                LOGGER.error("Failed to load statscore_config.json", e);
                resetToDefaults(manager);
            }
        }

        static void createDefaultConfig(File configFile) {
            JsonObject defaultConfig = new JsonObject();
            defaultConfig.addProperty("_comment", "DO NOT EDIT THIS FILE MANUALLY. Use /statsconfig commands to modify settings.");
            defaultConfig.addProperty("daily_reset_time", "00:00:00 UTC");
            defaultConfig.add("username_colors", new JsonObject());
            defaultConfig.add("blacklisted_players", new com.google.gson.JsonArray());

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(defaultConfig, writer);
                LOGGER.info("Created default statscore_config.json at {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to create default statscore_config.json", e);
            }
        }

        private static void resetToDefaults(ConfigManager manager) {
            LOGGER.warn("Resetting to default configuration");
            manager.usernameColors = Map.of();
            manager.blacklistedPlayers = Set.of();
            manager.dailyResetTime = "00:00:00 UTC";
            if (manager.dailyPlaytimeTracker != null) {
                manager.dailyPlaytimeTracker.setDailyResetTime(manager.dailyResetTime);
            } else {
                LOGGER.warn("Skipping dailyPlaytimeTracker.setDailyResetTime in resetToDefaults due to null tracker");
            }
        }
    }
}