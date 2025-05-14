package net.craftmaster08.cm08statscore.playtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.craftmaster08.cm08statscore.cache.PlaytimeUsernameCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tracks and retrieves player playtime data for online and offline players.
 */
public class PlaytimeTracker {
    private static final Logger LOGGER = LogManager.getLogger(PlaytimeTracker.class);
    public record PlayerPlaytime(String username, double playtime, UUID uuid) {}

    /**
     * Gets the overall playtime for all players, sorted by playtime in descending order.
     *
     * @param server The Minecraft server instance.
     * @return A list of player playtime records.
     */
    public static List<PlayerPlaytime> getOverallPlaytime(MinecraftServer server) {
        return Stream.concat(
                        getOnlinePlaytimes(server).stream(),
                        getOfflinePlaytimes(server).stream()
                ).sorted(Comparator.comparingDouble(PlayerPlaytime::playtime).reversed())
                .toList();
    }

    private static List<PlayerPlaytime> getOnlinePlaytimes(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .map(player -> new PlayerPlaytime(
                        player.getName().getString(),
                        player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20.0 / 3600.0,
                        player.getUUID()
                ))
                .toList();
    }

    private static List<PlayerPlaytime> getOfflinePlaytimes(MinecraftServer server) {
        List<PlayerPlaytime> playtimes = new ArrayList<>();
        File statsFolder = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
        if (!statsFolder.exists() || !statsFolder.isDirectory()) {
            return playtimes;
        }

        File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (statFiles == null) {
            return playtimes;
        }

        Set<UUID> onlineUUIDs = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .collect(Collectors.toSet());

        for (File statFile : statFiles) {
            try {
                String uuidString = statFile.getName().replace(".json", "");
                UUID uuid = UUID.fromString(uuidString);

                if (onlineUUIDs.contains(uuid)) {
                    continue;
                }

                JsonObject statsJson;
                try (FileReader reader = new FileReader(statFile)) {
                    statsJson = JsonParser.parseReader(reader).getAsJsonObject();
                }

                JsonObject stats = statsJson.getAsJsonObject("stats");
                if (stats != null) {
                    JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                    if (custom != null) {
                        JsonElement playTimeElement = custom.get("minecraft:play_time");
                        if (playTimeElement != null) {
                            double hours = playTimeElement.getAsLong() / 20.0 / 3600.0;
                            String username = UsernameResolver.resolve(server, uuid, uuidString);
                            playtimes.add(new PlayerPlaytime(username, hours, uuid));
                        }
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("Error reading stat file {}: {}", statFile.getName(), e.getMessage());
            }
        }

        return playtimes;
    }

    /**
     * Resolves usernames for player UUIDs using multiple sources.
     */
    private interface UsernameResolver {
        static String resolve(MinecraftServer server, UUID uuid, String uuidString) {
            // Try server profile cache
            String username = server.getProfileCache()
                    .get(uuid)
                    .map(profile -> {
                        String name = profile.getName();
                        if (name == null || name.isEmpty()) {
                            LOGGER.warn("Profile cache returned null/empty name for UUID: {}", uuidString);
                            return null;
                        }
                        return name;
                    })
                    .orElse(null);

            if (username != null) {
                return username;
            }

            // Try custom cache
            PlaytimeUsernameCache cache = PlaytimeUsernameCache.getInstance(server);
            username = cache.getUsername(uuid);
            if (username != null) {
                return username;
            }

            // Fallback to Mojang API
            try {
                HttpClient client = HttpClient.newHttpClient();
                String uuidNoHyphens = uuidString.replace("-", "");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/user/profile/" + uuidNoHyphens))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    username = json.get("name").getAsString();
                    if (username != null && !username.isEmpty()) {
                        cache.storeUsername(uuid, username);
                        return username;
                    }
                } else {
                    LOGGER.warn("Mojang API request failed for UUID: {}, status: {}", uuidString, response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error querying Mojang API for UUID: {}", uuidString, e);
            }

            // Final fallback
            return "Unknown_" + uuidString.substring(0, 8);
        }
    }
}