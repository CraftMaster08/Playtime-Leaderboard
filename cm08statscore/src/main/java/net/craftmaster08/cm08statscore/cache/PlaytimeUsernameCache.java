package net.craftmaster08.cm08statscore.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages a cache of player UUIDs to usernames, persisted to a JSON file.
 */
public class PlaytimeUsernameCache {
    private static final Logger LOGGER = LogManager.getLogger(PlaytimeUsernameCache.class);

    private static class Holder {
        private static volatile PlaytimeUsernameCache INSTANCE;
    }

    private final File cacheFile;
    private final Map<UUID, String> usernameMap;
    private final Gson gson;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private PlaytimeUsernameCache(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("MinecraftServer cannot be null");
        }
        this.cacheFile = new File(server.getServerDirectory(), "playtime_usernames.json");
        this.usernameMap = new HashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadCache();
    }

    /**
     * Gets the singleton instance of the username cache.
     *
     * @param server The Minecraft server instance.
     * @return The username cache instance.
     */
    public static PlaytimeUsernameCache getInstance(MinecraftServer server) {
        PlaytimeUsernameCache instance = Holder.INSTANCE;
        if (instance == null) {
            synchronized (PlaytimeUsernameCache.class) {
                instance = Holder.INSTANCE;
                if (instance == null) {
                    instance = new PlaytimeUsernameCache(server);
                    Holder.INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    /**
     * Gets the username for a given UUID.
     *
     * @param uuid The player's UUID.
     * @return The username, or null if not found.
     */
    public String getUsername(UUID uuid) {
        lock.readLock().lock();
        try {
            return usernameMap.get(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stores a username for a given UUID and saves the cache.
     *
     * @param uuid     The player's UUID.
     * @param username The player's username.
     */
    public void storeUsername(UUID uuid, String username) {
        lock.writeLock().lock();
        try {
            usernameMap.put(uuid, username);
            saveCache();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }
        lock.writeLock().lock();
        try (FileReader reader = new FileReader(cacheFile)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> rawMap = gson.fromJson(reader, type);
            if (rawMap != null) {
                for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        usernameMap.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in cache: {}", entry.getKey());
                    }
                }
                LOGGER.info("Loaded username cache with {} entries", usernameMap.size());
            }
        } catch (IOException e) {
            LOGGER.error("Error loading username cache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveCache() {
        lock.writeLock().lock();
        try {
            Map<String, String> rawMap = new HashMap<>();
            usernameMap.forEach((uuid, username) -> rawMap.put(uuid.toString(), username));
            try (FileWriter writer = new FileWriter(cacheFile)) {
                gson.toJson(rawMap, writer);
                LOGGER.info("Saved username cache");
            }
        } catch (IOException e) {
            LOGGER.error("Error saving username cache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}