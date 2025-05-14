package net.craftmaster08.playtimeleaderboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.craftmaster08.cm08statscore.StatsCore;
import net.craftmaster08.cm08statscore.config.ConfigManager;
import net.craftmaster08.cm08statscore.playtime.DailyPlaytimeTracker;
import net.craftmaster08.cm08statscore.playtime.PlaytimeTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers and executes the /playtime command to display a leaderboard of player playtimes.
 */
public class PlaytimeRunCommand {
    private static final Logger LOGGER = LogManager.getLogger(PlaytimeRunCommand.class);

    /**
     * Registers the /playtime command with the command dispatcher.
     *
     * @param dispatcher The command dispatcher to register the command with.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("playtime")
                .requires(source -> source.hasPermission(0))
                .executes(context -> new LeaderboardExecutor(context.getSource()).execute());

        try {
            dispatcher.register(command);
            LOGGER.info("Successfully registered /playtime command");
        } catch (Exception e) {
            LOGGER.error("Failed to register /playtime command", e);
        }
    }

    /**
     * Handles the execution of the /playtime command, fetching and displaying the leaderboard.
     */
    private static class LeaderboardExecutor {
        private final CommandSourceStack source;
        private final MinecraftServer server;
        private final ConfigManager config;
        private final DailyPlaytimeTracker dailyPlaytimeTracker;

        LeaderboardExecutor(CommandSourceStack source) {
            this.source = source;
            this.server = PlaytimeLeaderboard.getServer();
            this.config = StatsCore.getConfigManager();
            this.dailyPlaytimeTracker = StatsCore.getDailyPlaytimeTracker();
        }

        int execute() {
            if (server == null) {
                sendError("Server not initialized");
                return 0;
            }
            if (config == null) {
                sendError("StatsCore configuration not initialized");
                return 0;
            }
            if (dailyPlaytimeTracker == null) {
                LOGGER.warn("DailyPlaytimeTracker unavailable; daily playtime hover text disabled");
            }

            List<PlaytimeTracker.PlayerPlaytime> playtimes = fetchPlaytimes();
            if (playtimes == null) {
                return 0;
            }
            if (playtimes.isEmpty()) {
                source.sendSystemMessage(Component.literal("No playtime data available")
                        .withStyle(ChatFormatting.YELLOW));
                return 1;
            }

            LeaderboardFormatter formatter = new LeaderboardFormatter(
                    playtimes,
                    config.getBlacklistedPlayers(),
                    config.getUsernameColors(),
                    dailyPlaytimeTracker
            );
            formatter.displayLeaderboard(source);
            return 1;
        }

        private List<PlaytimeTracker.PlayerPlaytime> fetchPlaytimes() {
            try {
                return PlaytimeTracker.getOverallPlaytime(server);
            } catch (Exception e) {
                sendError("Failed to retrieve playtime data: " + e.getMessage());
                LOGGER.error("Failed to retrieve playtime data", e);
                return null;
            }
        }

        private void sendError(String message) {
            source.sendSystemMessage(Component.literal(message)
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Formats and displays the playtime leaderboard.
     */
    private static class LeaderboardFormatter {
        private static final int BASE_PADDING = 16;
        private static final int RANK_LENGTH = 3;

        private final List<PlaytimeTracker.PlayerPlaytime> playtimes;
        private final Set<String> blacklistedPlayers;
        private final Map<String, ChatFormatting> usernameColors;
        private final DailyPlaytimeTracker dailyPlaytimeTracker;

        LeaderboardFormatter(
                List<PlaytimeTracker.PlayerPlaytime> playtimes,
                Set<String> blacklistedPlayers,
                Map<String, ChatFormatting> usernameColors,
                DailyPlaytimeTracker dailyPlaytimeTracker
        ) {
            this.playtimes = playtimes;
            this.blacklistedPlayers = blacklistedPlayers;
            this.usernameColors = usernameColors;
            this.dailyPlaytimeTracker = dailyPlaytimeTracker;
        }

        void displayLeaderboard(CommandSourceStack source) {
            int maxUsernameLength = calculateMaxUsernameLength();
            int totalPadding = Math.max(BASE_PADDING, maxUsernameLength + RANK_LENGTH);
            int maxLineLength = calculateMaxLineLength(totalPadding);

            String border = "=".repeat(maxLineLength + 3);
            MutableComponent borderComponent = Component.literal(border)
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));

            source.sendSystemMessage(borderComponent);
            source.sendSystemMessage(Component.literal("Playtime:")
                    .withStyle(ChatFormatting.DARK_GREEN));

            for (int i = 0; i < playtimes.size(); i++) {
                formatPlayerEntry(source, playtimes.get(i), i + 1, totalPadding);
                if (i == 2 && playtimes.size() > 3) {
                    source.sendSystemMessage(Component.literal(""));
                }
            }

            source.sendSystemMessage(borderComponent);
        }

        private int calculateMaxUsernameLength() {
            return playtimes.stream()
                    .map(pt -> (pt.username() + ":").length())
                    .max(Integer::compareTo)
                    .orElse(0);
        }

        private int calculateMaxLineLength(int totalPadding) {
            int maxLineLength = 0;
            for (int i = 0; i < playtimes.size(); i++) {
                int lineLength = 0;
                PodiumRank rank = PodiumRank.fromPosition(i + 1);
                if (rank != PodiumRank.NONE) {
                    lineLength += RANK_LENGTH;
                }
                lineLength += totalPadding;

                double playtime = playtimes.get(i).playtime();
                String hoursText = playtime >= 1000.0 ? String.format("%d", (int) playtime) : String.format("%.2f", playtime);
                lineLength += hoursText.length() + 1;
                if (playtime >= 1000.0) {
                    lineLength += 1;
                }
                if (playtime >= 100.0) {
                    double days = playtime / 24.0;
                    lineLength += String.format("(%.2fd)", days).length() + 4;
                }
                maxLineLength = Math.max(maxLineLength, lineLength);
            }
            return Math.max(maxLineLength, 9);
        }

        private void formatPlayerEntry(CommandSourceStack source, PlaytimeTracker.PlayerPlaytime pt, int position, int totalPadding) {
            PodiumRank rank = PodiumRank.fromPosition(position);
            MutableComponent message = rank.formatRank();
            if (rank != PodiumRank.NONE) {
                message = message.append(Component.literal(" "));
            }

            String username = pt.username() + ":";
            int usernamePadding = (rank != PodiumRank.NONE) ? totalPadding - RANK_LENGTH : totalPadding;
            String paddedUsername = username + " ".repeat(Math.max(0, usernamePadding - username.length()));
            ChatFormatting usernameColor = usernameColors.getOrDefault(pt.username().toLowerCase(), ChatFormatting.WHITE);
            MutableComponent usernameComponent = Component.literal(paddedUsername)
                    .withStyle(Style.EMPTY.withColor(usernameColor).withBold(false));

            MutableComponent hours = HourRange.findRange(pt.playtime()).formatHours(pt.playtime());
            if (dailyPlaytimeTracker != null) {
                double dailyHours = dailyPlaytimeTracker.getDailyPlaytime(pt.uuid());
                String dailyText = DailyPlaytimeTracker.formatDailyPlaytime(dailyHours);
                hours = hours.withStyle(hours.getStyle().withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(dailyText))
                ));
            } else {
                hours = hours.withStyle(hours.getStyle().withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Daily playtime unavailable"))
                ));
            }

            message = message.append(usernameComponent).append(hours);

            if (pt.playtime() >= 100.0) {
                double days = pt.playtime() / 24.0;
                String daysText = String.format("    (%.2fd)", days);
                message = message.append(Component.literal(daysText)
                        .withStyle(Style.EMPTY.withColor(rank.getColor()).withBold(true)));
            }

            source.sendSystemMessage(message);
        }
    }

    /**
     * Represents a podium rank for the top 3 players in the leaderboard.
     */
    private enum PodiumRank {
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
            return color;
        }
    }

    /**
     * Defines hour ranges for formatting playtime with colors and symbols.
     */
    private enum HourRange {
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
        H_1900_1999(1900, 2000, new ChatFormatting[]{ChatFormatting.DARK_PURPLE}, ChatFormatting.GRAY, "✪", ChatFormatting.DARK_GRAY),
        H_2000_2099(2000, 2100, new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.WHITE, ChatFormatting.GRAY}, ChatFormatting.DARK_GRAY, "✪", ChatFormatting.GRAY);

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
            return H_2000_2099;
        }

        public MutableComponent formatHours(double playtime) {
            String hoursText = playtime >= 1000.0 ? String.format("%d", (int) playtime) : String.format("%.2f", playtime);
            MutableComponent component = Component.literal("");

            if (starSymbol != null) {
                component.append(Component.literal(starSymbol + " ")
                        .withStyle(Style.EMPTY.withColor(starColor).withBold(false)));
            }

            if (hoursColors.length > 1) {
                String[] chars = hoursText.split("");
                for (int i = 0; i < chars.length; i++) {
                    ChatFormatting color = i < hoursColors.length ? hoursColors[i] : ChatFormatting.WHITE;
                    component.append(Component.literal(chars[i])
                            .withStyle(Style.EMPTY.withColor(color).withBold(false)));
                }
            } else {
                component.append(Component.literal(hoursText)
                        .withStyle(Style.EMPTY.withColor(hoursColors[0]).withBold(false)));
            }

            component.append(Component.literal("h")
                    .withStyle(Style.EMPTY.withColor(hColor).withBold(false)));

            return component;
        }
    }
}