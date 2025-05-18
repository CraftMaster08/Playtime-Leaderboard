package net.craftmaster08.cm08statscore.playtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
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

/**
 * Tracks daily playtime for players, resetting at a configurable time.
 */
public class DailyPlaytimeTracker {
    private static final Logger LOGGER = LogManager.getLogger(DailyPlaytimeTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataPath;
    private final MinecraftServer server;
    private final ResetScheduler resetScheduler;
    private final Map<UUID, Double> dailyPlaytimes;
    private final Map<UUID, Long> lastKnownTicks;
    private final Stat<?> playTimeStat;

    public DailyPlaytimeTracker(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("MinecraftServer cannot be null");
        }
        this.server = server;
        this.dataPath = server.getWorldPath(LevelResource.ROOT).resolve("playtime_daily.json");
        this.dailyPlaytimes = new HashMap<>();
        this.lastKnownTicks = new HashMap<>();
        this.resetScheduler = new ResetScheduler(this);
        try {
            this.playTimeStat = Stats.CUSTOM.get(Stats.PLAY_TIME);
            if (this.playTimeStat == null) {
                throw new IllegalStateException("Stats.PLAY_TIME not found in Stats.CUSTOM");
            }
            LOGGER.info("Successfully accessed Stats.PLAY_TIME");
        } catch (Exception e) {
            LOGGER.error("Failed to access Stats.PLAY_TIME", e);
            throw new RuntimeException("Cannot initialize DailyPlaytimeTracker without Stats.PLAY_TIME", e);
        }
        setDailyResetTime("00:00:00 UTC");
        loadData();
    }

    /**
     * Sets the daily reset time for playtime tracking.
     *
     * @param timeStr The reset time in format "HH:mm:ss UTC".
     */
    public void setDailyResetTime(String timeStr) {
        resetScheduler.setDailyResetTime(timeStr);
    }

    /**
     * Gets the daily playtime for a player.
     *
     * @param uuid The player's UUID.
     * @return The daily playtime in hours.
     */
    public double getDailyPlaytime(UUID uuid) {
        return dailyPlaytimes.getOrDefault(uuid, 0.0);
    }

    /**
     * Updates a player's playtime based on their current ticks.
     *
     * @param player The player to update.
     */
    public void updatePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long currentTicks = player.getStats().getValue(playTimeStat);
        long lastTicks = lastKnownTicks.getOrDefault(uuid, currentTicks);

        double hoursPlayed = (currentTicks - lastTicks) / 20.0 / 3600.0;
        dailyPlaytimes.merge(uuid, hoursPlayed, Double::sum);
        lastKnownTicks.put(uuid, currentTicks);

        resetScheduler.checkReset();
    }

    /**
     * Handles a player logging in, initializing their playtime tracking.
     *
     * @param player The player who logged in.
     */
    public void playerLoggedIn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long currentTicks = player.getStats().getValue(playTimeStat);
        lastKnownTicks.put(uuid, currentTicks);
        dailyPlaytimes.putIfAbsent(uuid, 0.0);
    }

    /**
     * Handles a player logging out, updating and saving their playtime.
     *
     * @param player The player who logged out.
     */
    public void playerLoggedOut(ServerPlayer player) {
        updatePlayer(player);
        saveData();
    }

    private void loadData() {
        DataSerializer.load(dataPath, dailyPlaytimes, resetScheduler);
    }

    private void saveData() {
        DataSerializer.save(dataPath, dailyPlaytimes, resetScheduler.getLastResetCheck());
    }

    /**
     * Formats daily playtime as a human-readable string.
     *
     * @param hours The playtime in hours.
     * @return A formatted string (e.g., "2h 30min 15sec today").
     */
    public static String formatDailyPlaytime(double hours) {
        double totalSecondsDouble = hours * 3600.0;
        int h = (int) (totalSecondsDouble / 3600);
        double remainingSeconds = totalSecondsDouble % 3600;
        int m = (int) (remainingSeconds / 60);
        int s = (int) (remainingSeconds % 60);
        return String.format("%dh %dmin %dsec today", h, m, s);
    }

    /**
     * Manages daily reset scheduling for playtime tracking.
     */
    private static class ResetScheduler {
        private String dailyResetTime;
        private LocalTime resetTime;
        private Instant lastResetCheck;
        private final DailyPlaytimeTracker tracker;

        ResetScheduler(DailyPlaytimeTracker tracker) {
            this.tracker = tracker;
            this.lastResetCheck = Instant.now();
        }

        void setDailyResetTime(String timeStr) {
            try {
                String[] parts = timeStr.split(" ");
                if (parts.length != 1) {
                    throw new DateTimeParseException("Invalid format, expected 'HH:mm:ss UTC'", timeStr, 0);
                }
                this.resetTime = LocalTime.parse(parts[0], DateTimeFormatter.ofPattern("HH:mm:ss"));
                String timeUTC = timeStr + " UTC";
                this.dailyResetTime = timeUTC;
                LOGGER.info("Set daily reset time to: {}", timeUTC);
            } catch (DateTimeParseException e) {
                LOGGER.error("Invalid daily_reset_time format: {}. Defaulting to 00:00:00 UTC", timeStr, e);
                this.resetTime = LocalTime.of(0, 0, 0);
                this.dailyResetTime = "00:00:00 UTC";
            }
        }

        void checkReset() {
            Instant now = Instant.now();
            ZonedDateTime currentZdt = ZonedDateTime.ofInstant(now, ZoneId.of("UTC"));
            ZonedDateTime lastCheckZdt = ZonedDateTime.ofInstant(lastResetCheck, ZoneId.of("UTC"));
            LocalDate today = currentZdt.toLocalDate();
            ZonedDateTime todayReset = ZonedDateTime.of(today, resetTime, ZoneId.of("UTC"));

            if (currentZdt.isAfter(todayReset) && lastCheckZdt.isBefore(todayReset)) {
                LOGGER.info("Resetting daily playtime at {}", currentZdt);
                tracker.dailyPlaytimes.replaceAll((uuid, v) -> 0.0);
                tracker.saveData();
            }

            if (!currentZdt.toLocalDate().equals(lastCheckZdt.toLocalDate())) {
                ZonedDateTime tomorrowReset = todayReset.plusDays(1);
                if (currentZdt.isAfter(tomorrowReset) && lastCheckZdt.isBefore(tomorrowReset)) {
                    LOGGER.info("Resetting daily playtime at {}", currentZdt);
                    tracker.dailyPlaytimes.replaceAll((uuid, v) -> 0.0);
                    tracker.saveData();
                }
            }

            lastResetCheck = now;
        }

        Instant getLastResetCheck() {
            return lastResetCheck;
        }
    }

    /**
     * Handles serialization and deserialization of daily playtime data.
     */
    private static class DataSerializer {
        static void load(Path dataPath, Map<UUID, Double> dailyPlaytimes, ResetScheduler resetScheduler) {
            File dataFile = dataPath.toFile();
            if (!dataFile.exists()) {
                save(dataPath, dailyPlaytimes, resetScheduler.getLastResetCheck());
                return;
            }

            try (FileReader reader = new FileReader(dataFile)) {
                JsonObject dataJson = GSON.fromJson(reader, JsonObject.class);
                if (dataJson == null) {
                    throw new JsonParseException("Daily playtime file is empty or invalid JSON");
                }

                if (dataJson.has("daily_playtimes")) {
                    JsonObject playtimesJson = dataJson.getAsJsonObject("daily_playtimes");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : playtimesJson.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            dailyPlaytimes.put(uuid, entry.getValue().getAsDouble());
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("Invalid UUID in playtime data: {}", entry.getKey());
                        }
                    }
                }

                if (dataJson.has("last_reset_check")) {
                    try {
                        resetScheduler.lastResetCheck = Instant.parse(dataJson.get("last_reset_check").getAsString());
                    } catch (DateTimeParseException e) {
                        LOGGER.warn("Invalid last_reset_check format, using current time");
                        resetScheduler.lastResetCheck = Instant.now();
                    }
                }

                LOGGER.info("Successfully loaded playtime_daily.json");
            } catch (IOException | JsonParseException e) {
                LOGGER.error("Failed to load playtime_daily.json", e);
                dailyPlaytimes.clear();
                resetScheduler.lastResetCheck = Instant.now();
            }
        }

        static void save(Path dataPath, Map<UUID, Double> dailyPlaytimes, Instant lastResetCheck) {
            JsonObject dataJson = new JsonObject();
            JsonObject playtimesJson = new JsonObject();
            dailyPlaytimes.forEach((uuid, hours) -> playtimesJson.addProperty(uuid.toString(), hours));
            dataJson.add("daily_playtimes", playtimesJson);
            dataJson.addProperty("last_reset_check", lastResetCheck.toString());

            try (FileWriter writer = new FileWriter(dataPath.toFile())) {
                GSON.toJson(dataJson, writer);
                LOGGER.info("Saved playtime_daily.json");
            } catch (IOException e) {
                LOGGER.error("Failed to save playtime_daily.json", e);
            }
        }
    }
}