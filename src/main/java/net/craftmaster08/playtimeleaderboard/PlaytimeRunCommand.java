package net.craftmaster08.playtimeleaderboard;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

public class PlaytimeRunCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        PlaytimeRunCommand runCommand = new PlaytimeRunCommand();
        dispatcher.register(
                Commands.literal("playtime")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> execute(context.getSource()))
        );
    }

    private enum PodiumRank
    {
        FIRST(1, ChatFormatting.GOLD, true),
        SECOND(2, ChatFormatting.WHITE, true),
        THIRD(3, ChatFormatting.DARK_PURPLE, true),
        NONE(0, ChatFormatting.WHITE, false);

        private final int rank;
        private final ChatFormatting color;
        private final boolean isBold;

        PodiumRank(int rank, ChatFormatting color, boolean isBold) {
            this.rank = rank;
            this.color = color;
            this.isBold = isBold;
        }

        public static PodiumRank fromPosition(int position) {
            return switch (position) {
                case 1 -> FIRST;
                case 2 -> SECOND;
                case 3 -> THIRD;
                default -> NONE;
            };
        }

        public MutableComponent formatRank() {
            if (this == NONE) {
                return Component.literal("");
            }
            return Component.literal(rank + ".")
                    .withStyle(Style.EMPTY.withColor(color).withBold(isBold));
        }

        public ChatFormatting getColor() {
            return color; // Used for username and days
        }
    }

    private enum HourRange
    {
        UNDER_100(0, 100, new ChatFormatting[]{ChatFormatting.GRAY}, ChatFormatting.GRAY, null, null),
        H_100_199(100, 200, new ChatFormatting[]{ChatFormatting.WHITE}, ChatFormatting.WHITE, null, null),
        H_200_299(200, 300, new ChatFormatting[]{ChatFormatting.GOLD}, ChatFormatting.GOLD, null, null),
        H_300_399(300, 400, new ChatFormatting[]{ChatFormatting.AQUA}, ChatFormatting.AQUA, null, null),
        H_400_499(400, 500, new ChatFormatting[]{ChatFormatting.DARK_GREEN}, ChatFormatting.DARK_GREEN, null, null),
        H_500_599(500, 600, new ChatFormatting[]{ChatFormatting.DARK_AQUA}, ChatFormatting.DARK_AQUA, null, null),
        H_600_699(600, 700, new ChatFormatting[]{ChatFormatting.DARK_RED}, ChatFormatting.DARK_RED, null, null),
        H_700_799(700, 800, new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE}, ChatFormatting.LIGHT_PURPLE, null, null),
        H_800_899(800, 900, new ChatFormatting[]{ChatFormatting.BLUE}, ChatFormatting.BLUE, null, null),
        H_900_999(900, 1000, new ChatFormatting[]{ChatFormatting.DARK_PURPLE}, ChatFormatting.DARK_PURPLE, null, null),
        H_1000_1099(1000, 1100, new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.AQUA}, ChatFormatting.LIGHT_PURPLE, "✫", ChatFormatting.RED),
        H_1100_1199(1100, 1200, new ChatFormatting[]{ChatFormatting.WHITE}, ChatFormatting.GRAY, "✪", ChatFormatting.GRAY),
        H_1200_1299(1200, 1300, new ChatFormatting[]{ChatFormatting.YELLOW}, ChatFormatting.GRAY, "✪", ChatFormatting.GOLD),
        H_1300_1399(1300, 1400, new ChatFormatting[]{ChatFormatting.AQUA}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_AQUA),
        H_1400_1499(1400, 1500, new ChatFormatting[]{ChatFormatting.GREEN}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_GREEN),
        H_1500_1599(1500, 1600, new ChatFormatting[]{ChatFormatting.DARK_AQUA}, ChatFormatting.GRAY, "✪", ChatFormatting.BLUE),
        H_1600_1699(1600, 1700, new ChatFormatting[]{ChatFormatting.RED}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_RED),
        H_1700_1799(1700, 1800, new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_PURPLE),
        H_1800_1899(1800, 1900, new ChatFormatting[]{ChatFormatting.BLUE}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_BLUE),
        H_1900_1999(1900, 2000, new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.WHITE, ChatFormatting.GRAY}, ChatFormatting.DARK_GRAY, "✪", ChatFormatting.GRAY),
        H_2000_2099(2000, 2100, new ChatFormatting[]{ChatFormatting.WHITE}, ChatFormatting.GRAY, "✪", ChatFormatting.GRAY);

        private final double minHours;
        private final double maxHours;
        private final ChatFormatting[] hoursColors;
        private final ChatFormatting hColor;
        private final String starSymbol;
        private final ChatFormatting starColor;

        HourRange(double minHours, double maxHours, ChatFormatting[] hoursColors, ChatFormatting hColor, String starSymbol, ChatFormatting starColor) {
            this.minHours = minHours;
            this.maxHours = maxHours;
            this.hoursColors = hoursColors;
            this.hColor = hColor;
            this.starSymbol = starSymbol;
            this.starColor = starColor;
        }

        public static HourRange findRange(double playtime) {
            for (HourRange range : values()) {
                if (playtime >= range.minHours && playtime < range.maxHours) {
                    return range;
                }
            }
            return H_2000_2099; // Default for >=2100h
        }

        public MutableComponent formatHours(double playtime) {
            // Use integer formatting for >=1000h, double for <1000h
            String hoursText = playtime >= 1000.0 ? String.format("%d", (int) playtime) : String.format("%.2f", playtime);
            MutableComponent component = Component.literal("");

            // Add star symbol if present
            if (starSymbol != null) {
                component.append(Component.literal(starSymbol + " ")
                        .withStyle(Style.EMPTY.withColor(starColor).withBold(false)));
            }

            // Apply colors to hours
            if (hoursColors.length > 1) {
                // Per-digit coloring (1000-1099h, 1900-1999h)
                String[] chars = hoursText.split("");
                for (int i = 0; i < chars.length; i++) {
                    // Use hoursColors for digits 0-3, default to WHITE for others
                    ChatFormatting color = i < hoursColors.length ? hoursColors[i] : ChatFormatting.WHITE;
                    component.append(Component.literal(chars[i])
                            .withStyle(Style.EMPTY.withColor(color).withBold(false)));
                }
            } else {
                // Single color for hours
                component.append(Component.literal(hoursText)
                        .withStyle(Style.EMPTY.withColor(hoursColors[0]).withBold(false)));
            }

            // Add h with its color
            component.append(Component.literal("h")
                    .withStyle(Style.EMPTY.withColor(hColor).withBold(false)));

            return component;
        }
    }

    private static int execute(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<PlayerPlaytime> playtimes = new ArrayList<>();

        // Online players
        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer player : onlinePlayers) {
            playtimes.add(new PlayerPlaytime(
                    player.getName().getString(),
                    player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20.0 / 3600.0
            ));
        }

        // Offline players
        File statsFolder = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
        if (statsFolder.exists() && statsFolder.isDirectory()) {
            File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (statFiles != null) {
                for (File statFile : statFiles) {
                    try {
                        String uuidString = statFile.getName().replace(".json", "");
                        UUID uuid = UUID.fromString(uuidString);

                        if (onlinePlayers.stream().anyMatch(p -> p.getUUID().equals(uuid))) {
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
                                    String username = getUsernameFromUUID(server, uuid, uuidString);
                                    playtimes.add(new PlayerPlaytime(username, hours));
                                }
                            }
                        }
                    } catch (IOException | IllegalArgumentException e) {
                        System.err.println("Error reading stat file " + statFile.getName() + ": " + e.getMessage());
                    }
                }
            }
        }

        playtimes = playtimes.stream()
                .sorted(Comparator.comparingDouble(PlayerPlaytime::playtime).reversed())
                .collect(Collectors.toList());

        // Calculate max username lengths for podium and non-podium (including :)
        int maxPodiumUsernameLength = 0;
        int maxNonPodiumUsernameLength = 0;
        for (int i = 0; i < playtimes.size(); i++) {
            String username = playtimes.get(i).username() + ":";
            int length = username.length();
            if (i < 3) {
                maxPodiumUsernameLength = Math.max(maxPodiumUsernameLength, length);
            } else {
                maxNonPodiumUsernameLength = Math.max(maxNonPodiumUsernameLength, length);
            }
        }
        // Adjust padding for Minecraft chat rendering
        final int basePadding = 16; // Minimum padding to ensure alignment in chat
        final int podiumPadding = Math.max(basePadding, maxPodiumUsernameLength) + 6; // Extra 6 for podium
        final int nonPodiumPadding = Math.max(basePadding, maxNonPodiumUsernameLength);

        // Calculate the longest line length (approximate visible characters)
        int maxLineLength = 0;
        for (int i = 0; i < playtimes.size(); i++) {
            PlayerPlaytime pt = playtimes.get(i);
            int lineLength = 0;

            // Add rank length (e.g., "1. " for podium)
            PodiumRank rank = PodiumRank.fromPosition(i + 1);
            if (rank != PodiumRank.NONE) {
                lineLength += 3; // "1. " (2 chars + 1 space)
            }

            // Add username length (without extra padding spaces for length calc)
            String username = pt.username() + ":";
            lineLength += username.length();

            // Padding spaces: don't count for visible width
            int paddingLength = (i < 3) ? podiumPadding : nonPodiumPadding;
            // int paddingSpaces = Math.max(0, paddingLength - username.length()); // Not counted

            // Add hours length (including star if present)
            double playtime = pt.playtime();
            String hoursText = playtime >= 1000.0 ? String.format("%d", (int) playtime) : String.format("%.2f", playtime);
            lineLength += hoursText.length() + 1; // +1 for "h"
            if (playtime >= 1000.0) {
                lineLength += 0; // Star (✫ or ✪) + space, count as 0 chars for width
            }

            // Add days length if applicable (reduce leading spaces in width calc)
            if (playtime >= 100.0) {
                double days = playtime / 24.0;
                String daysText = String.format("(%.2fd)", days); // Exclude "    " from width
                lineLength += daysText.length() + 2; // Add 2 for leading spaces (instead of 4)
            }

            maxLineLength = Math.max(maxLineLength, lineLength);
        }
        // Add "Playtime:" length (9 chars)
        maxLineLength = Math.max(maxLineLength, 9);

        // Create border: 3 characters longer than the longest line
        int borderLength = maxLineLength + 3;
        String border = "=".repeat(borderLength);
        MutableComponent borderComponent = Component.literal(border)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));

        // Send top border
        source.sendSystemMessage(borderComponent);
        source.sendSystemMessage(Component.literal("Playtime:").withStyle(ChatFormatting.DARK_GREEN));
        for (int i = 0; i < playtimes.size(); i++) {
            PlayerPlaytime pt = playtimes.get(i);
            PodiumRank rank = PodiumRank.fromPosition(i + 1);

            MutableComponent message = rank.formatRank();
            if (rank != PodiumRank.NONE) {
                message = message.append(Component.literal(" "));
            }

            // Pad username with : to align hours
            String username = pt.username() + ":";
            int paddingLength = (i < 3) ? podiumPadding : nonPodiumPadding;
            String paddedUsername = username + " ".repeat(Math.max(0, paddingLength - username.length()));
            MutableComponent usernameComponent = Component.literal(paddedUsername)
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false));

            MutableComponent hours = HourRange.findRange(pt.playtime()).formatHours(pt.playtime());

            message = message.append(usernameComponent).append(hours);

            if (pt.playtime() >= 100.0) {
                double days = pt.playtime / 24.0;
                String daysText = String.format("    (%.2fd)", days);
                message = message.append(Component.literal(daysText)
                        .withStyle(Style.EMPTY.withColor(rank.getColor()).withBold(true)));
            }

            source.sendSystemMessage(message);

            // Add empty line after podium (positions 1-3)
            if (i == 2 && playtimes.size() > 3) {
                source.sendSystemMessage(Component.literal(""));
            }
        }
        // Send bottom border
        source.sendSystemMessage(borderComponent);

        return 1;
    }

    private static String getUsernameFromUUID(MinecraftServer server, UUID uuid, String uuidString)
    {
        // Try server profile cache first
        String username = server.getProfileCache()
                .get(uuid)
                .map(profile -> {
                    String name = profile.getName();
                    if (name == null || name.isEmpty()) {
                        System.err.println("Profile cache returned null/empty name for UUID: " + uuidString);
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
                    // Store in custom cache
                    cache.storeUsername(uuid, username);
                    return username;
                }
            } else {
                System.err.println("Mojang API request failed for UUID: " + uuidString + ", status: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error querying Mojang API for UUID: " + uuidString + ": " + e.getMessage());
        }

        // Final fallback: Unknown_<uuid_prefix>
        return "Unknown_" + uuidString.substring(0, 8);
    }

    private record PlayerPlaytime(String username, double playtime) {}
}