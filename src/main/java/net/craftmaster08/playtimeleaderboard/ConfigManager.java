package net.craftmaster08.playtimeleaderboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("playtimeleaderboard_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, ChatFormatting> usernameColors = new HashMap<>();
    private Set<String> blacklistedPlayers = new HashSet<>();
    private String dailyResetTime = "00:00:00 UTC"; // Default value
    private final DailyPlaytimeTracker dailyPlaytimeTracker;

    public ConfigManager(DailyPlaytimeTracker dailyPlaytimeTracker) {
        this.dailyPlaytimeTracker = dailyPlaytimeTracker;
        loadConfig();
    }

    public Map<String, ChatFormatting> getUsernameColors() {
        return usernameColors;
    }

    public Set<String> getBlacklistedPlayers() {
        return blacklistedPlayers;
    }

    public void loadConfig() {
        File configFile = CONFIG_PATH.toFile();

        // Create default config if file doesn't exist
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        // Load and parse config
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson = GSON.fromJson(reader, JsonObject.class);
            if (configJson == null) {
                throw new JsonParseException("Config file is empty or invalid JSON");
            }

            // Parse username colors
            Map<String, ChatFormatting> tempUsernameColors = new HashMap<>();
            if (configJson.has("username_colors")) {
                JsonObject usernameColorsJson = configJson.getAsJsonObject("username_colors");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : usernameColorsJson.entrySet()) {
                    String username = entry.getKey();
                    String colorName = entry.getValue().getAsString().toUpperCase();
                    ChatFormatting color = ChatFormatting.getByName(colorName);
                    if (color == null || !color.isColor()) {
                        throw new JsonParseException("Invalid Minecraft color: " + colorName);
                    }
                    tempUsernameColors.put(username.toLowerCase(), color);
                }
            }

            // Parse blacklisted players
            Set<String> tempBlacklistedPlayers = new HashSet<>();
            if (configJson.has("blacklisted_players")) {
                for (com.google.gson.JsonElement element : configJson.getAsJsonArray("blacklisted_players")) {
                    tempBlacklistedPlayers.add(element.getAsString().toLowerCase());
                }
            }

            // Parse daily reset time
            String tempDailyResetTime = "00:00:00 UTC";
            if (configJson.has("daily_reset_time")) {
                tempDailyResetTime = configJson.get("daily_reset_time").getAsString();
            }

            // If parsing succeeds, update the fields
            this.usernameColors = tempUsernameColors;
            this.blacklistedPlayers = tempBlacklistedPlayers;
            this.dailyResetTime = tempDailyResetTime;
            dailyPlaytimeTracker.setDailyResetTime(this.dailyResetTime);
            LOGGER.info("Successfully loaded playtimeleaderboard_config.json");
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load playtimeleaderboard_config.json: " + e.getMessage());
            LOGGER.error("Defaulting to white usernames, no blacklisted players, and midnight UTC reset.");
            this.usernameColors = new HashMap<>();
            this.blacklistedPlayers = new HashSet<>();
            this.dailyResetTime = "00:00:00 UTC";
            dailyPlaytimeTracker.setDailyResetTime(this.dailyResetTime);
        }
    }

    private void createDefaultConfig(File configFile) {
        JsonObject defaultConfig = new JsonObject();

        // Add comments (as a JSON field)
        defaultConfig.addProperty("daily_reset_time","00:00:00 UTC");

        // Write default config to file
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(defaultConfig, writer);
            LOGGER.info("Created default playtimeleaderboard_config.json at " + configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to create default playtimeleaderboard_config.json: " + e.getMessage());
        }
    }
}