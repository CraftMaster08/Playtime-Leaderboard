package net.craftmaster08.playtimeleaderboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlaytimeUsernameCache {
    private static PlaytimeUsernameCache instance;
    private final File cacheFile;
    private final Map<UUID, String> usernameMap;
    private final Gson gson;

    private PlaytimeUsernameCache(MinecraftServer server) {
        this.cacheFile = new File(server.getServerDirectory(), "playtime_usernames.json");
        this.usernameMap = new HashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadCache();
    }

    public static PlaytimeUsernameCache getInstance(MinecraftServer server) {
        if (instance == null) {
            instance = new PlaytimeUsernameCache(server);
        }
        return instance;
    }

    public String getUsername(UUID uuid) {
        return usernameMap.get(uuid);
    }

    public void storeUsername(UUID uuid, String username) {
        usernameMap.put(uuid, username);
        saveCache();
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(cacheFile)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> rawMap = gson.fromJson(reader, type);
            if (rawMap != null) {
                for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        usernameMap.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid UUID in cache: " + entry.getKey());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading username cache: " + e.getMessage());
        }
    }

    private void saveCache() {
        Map<String, String> rawMap = new HashMap<>();
        for (Map.Entry<UUID, String> entry : usernameMap.entrySet()) {
            rawMap.put(entry.getKey().toString(), entry.getValue());
        }
        try (FileWriter writer = new FileWriter(cacheFile)) {
            gson.toJson(rawMap, writer);
        } catch (IOException e) {
            System.err.println("Error saving username cache: " + e.getMessage());
        }
    }
}