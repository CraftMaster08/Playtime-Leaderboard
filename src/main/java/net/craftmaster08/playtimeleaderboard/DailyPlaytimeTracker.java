package net.craftmaster08.playtimeleaderboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DailyPlaytimeTracker {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataPath;
    private final MinecraftServer server;
    private String dailyResetTime; // Format: "HH:mm:ss UTC"
    private LocalTime resetTime;
    private Instant lastResetCheck;
    private Map<UUID, Double> dailyPlaytimes; // UUID -> Daily playtime in hours
    private Map<UUID, Long> lastKnownTicks; // UUID -> Last known play_time ticks

    public DailyPlaytimeTracker(MinecraftServer server) {
        this.server = server;
        this.dataPath = server.getWorldPath(LevelResource.ROOT).resolve("playtime_daily.json");
        this.dailyPlaytimes = new HashMap<>();
        this.lastKnownTicks = new HashMap<>();
        this.lastResetCheck = Instant.now();
        setDailyResetTime("00:00:00 UTC"); // Default to midnight UTC
        loadData();
    }

    public void setDailyResetTime(String timeStr) {
        try {
            // Parse "HH:mm:ss UTC" format
            String[] parts = timeStr.split(" ");
            if (parts.length != 2 || !parts[1].equals("UTC")) {
                throw new DateTimeParseException("Invalid format, expected 'HH:mm:ss UTC'", timeStr, 0);
            }
            this.resetTime = LocalTime.parse(parts[0], DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.dailyResetTime = timeStr;
            LOGGER.info("Set daily reset time to: " + timeStr);
        } catch (DateTimeParseException e) {
            LOGGER.error("Invalid daily_reset_time format: " + timeStr + ". Defaulting to 00:00:00 UTC.");
            this.resetTime = LocalTime.of(0, 0, 0);
            this.dailyResetTime = "00:00:00 UTC";
        }
    }

    public double getDailyPlaytime(UUID uuid) {
        return dailyPlaytimes.getOrDefault(uuid, 0.0);
    }

    public void updatePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long currentTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long lastTicks = lastKnownTicks.getOrDefault(uuid, currentTicks);

        // Calculate time played since last update (in hours)
        double hoursPlayed = (currentTicks - lastTicks) / 20.0 / 3600.0;
        double currentDaily = dailyPlaytimes.getOrDefault(uuid, 0.0);
        dailyPlaytimes.put(uuid, currentDaily + hoursPlayed);
        lastKnownTicks.put(uuid, currentTicks);

        // Check if we need to reset daily playtime
        checkReset();
    }

    public void playerLoggedIn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long currentTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        lastKnownTicks.put(uuid, currentTicks);
        if (!dailyPlaytimes.containsKey(uuid)) {
            dailyPlaytimes.put(uuid, 0.0);
        }
    }

    public void playerLoggedOut(ServerPlayer player) {
        updatePlayer(player);
        saveData();
    }

    private void checkReset() {
        Instant now = Instant.now();
        ZonedDateTime currentZdt = ZonedDateTime.ofInstant(now, ZoneId.of("UTC"));
        ZonedDateTime lastCheckZdt = ZonedDateTime.ofInstant(lastResetCheck, ZoneId.of("UTC"));

        // Get today's reset time
        LocalDate today = currentZdt.toLocalDate();
        ZonedDateTime todayReset = ZonedDateTime.of(today, resetTime, ZoneId.of("UTC"));

        // If current time is past today's reset and last check was before it, reset
        if (currentZdt.isAfter(todayReset) && lastCheckZdt.isBefore(todayReset)) {
            LOGGER.info("Resetting daily playtime at " + currentZdt);
            dailyPlaytimes.replaceAll((uuid, v) -> 0.0);
            saveData();
        }

        // If we've crossed into the next day, check the next day's reset
        if (!currentZdt.toLocalDate().equals(lastCheckZdt.toLocalDate())) {
            ZonedDateTime tomorrowReset = todayReset.plusDays(1);
            if (currentZdt.isAfter(tomorrowReset) && lastCheckZdt.isBefore(tomorrowReset)) {
                LOGGER.info("Resetting daily playtime at " + currentZdt);
                dailyPlaytimes.replaceAll((uuid, v) -> 0.0);
                saveData();
            }
        }

        lastResetCheck = now;
    }

    private void loadData() {
        File dataFile = dataPath.toFile();
        if (!dataFile.exists()) {
            saveData();
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject dataJson = GSON.fromJson(reader, JsonObject.class);
            if (dataJson == null) {
                throw new JsonParseException("Daily playtime file is empty or invalid JSON");
            }

            // Load daily playtimes
            if (dataJson.has("daily_playtimes")) {
                JsonObject playtimesJson = dataJson.getAsJsonObject("daily_playtimes");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : playtimesJson.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    double hours = entry.getValue().getAsDouble();
                    dailyPlaytimes.put(uuid, hours);
                }
            }

            // Load last reset check
            if (dataJson.has("last_reset_check")) {
                lastResetCheck = Instant.parse(dataJson.get("last_reset_check").getAsString());
            }

            LOGGER.info("Successfully loaded playtime_daily.json");
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load playtime_daily.json: " + e.getMessage());
            dailyPlaytimes.clear();
            lastResetCheck = Instant.now();
        }
    }

    private void saveData() {
        JsonObject dataJson = new JsonObject();

        // Save daily playtimes
        JsonObject playtimesJson = new JsonObject();
        for (Map.Entry<UUID, Double> entry : dailyPlaytimes.entrySet()) {
            playtimesJson.addProperty(entry.getKey().toString(), entry.getValue());
        }
        dataJson.add("daily_playtimes", playtimesJson);

        // Save last reset check
        dataJson.addProperty("last_reset_check", lastResetCheck.toString());

        try (FileWriter writer = new FileWriter(dataPath.toFile())) {
            GSON.toJson(dataJson, writer);
            LOGGER.info("Saved playtime_daily.json");
        } catch (IOException e) {
            LOGGER.error("Failed to save playtime_daily.json: " + e.getMessage());
        }
    }

    public static String formatDailyPlaytime(double hours) {
        // Convert hours to total seconds as a double to preserve precision
        double totalSecondsDouble = hours * 3600.0;
        // Extract hours
        int h = (int) (totalSecondsDouble / 3600);
        // Remaining seconds after hours
        double remainingSeconds = totalSecondsDouble % 3600;
        // Extract minutes
        int m = (int) (remainingSeconds / 60);
        // Remaining seconds after minutes
        int s = (int) (remainingSeconds % 60);
        return String.format("%dh %dmin %dsec today", h, m, s);
    }
}