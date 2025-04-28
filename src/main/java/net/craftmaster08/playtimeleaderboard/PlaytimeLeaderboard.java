package net.craftmaster08.playtimeleaderboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(PlaytimeLeaderboard.MODID)
public class PlaytimeLeaderboard
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "playtimeleaderboard";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static MinecraftServer server;
    private static ConfigManager configManager;
    private static PlaytimeUsernameCache usernameCache;
    private static DailyPlaytimeTracker dailyPlaytimeTracker;

    public PlaytimeLeaderboard(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
/*
        modEventBus.addListener(this::commonSetup);
*/
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        MinecraftForge.EVENT_BUS.register(this);
    }
/*
    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }
*/
    private void registerCommands(final RegisterCommandsEvent event)
    {
        PlaytimeRunCommand.register(event.getDispatcher());
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        server = event.getServer();
        dailyPlaytimeTracker = new DailyPlaytimeTracker(server);
        configManager = new ConfigManager(dailyPlaytimeTracker);
        usernameCache = PlaytimeUsernameCache.getInstance(server);
    }
/*
    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            //logging
        }
    }
*/

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer) {
            // Update daily playtime every 100 ticks (5 seconds)
            if (event.player.tickCount % 100 == 0) {
                dailyPlaytimeTracker.updatePlayer((ServerPlayer) event.player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            dailyPlaytimeTracker.playerLoggedIn((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            dailyPlaytimeTracker.playerLoggedOut((ServerPlayer) event.getEntity());
        }
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static PlaytimeUsernameCache getUsernameCache() {
        return usernameCache;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static DailyPlaytimeTracker getDailyPlaytimeTracker() {
        return dailyPlaytimeTracker;
    }
}