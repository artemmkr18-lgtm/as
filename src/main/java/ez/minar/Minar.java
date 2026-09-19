package ez.minar;

import ez.minar.system.api.FunctionManager;
import ez.minar.system.events.EventBus;
import ez.minar.system.events.EventManager;
import ez.minar.system.commands.MinarClientCommands;
import ez.minar.system.features.render.BlockOverlay;
import ez.minar.system.features.render.BlockESP;
import ez.minar.system.features.render.EntityESP;
import ez.minar.system.features.render.HitWave;
import ez.minar.system.features.render.FireFly;
import ez.minar.system.features.render.JumpCircles;
import ez.minar.system.features.render.KillEffect;
import ez.minar.system.features.render.Particles;
import ez.minar.system.features.render.Predictions;
import ez.minar.system.features.render.ShaderSky;
import ez.minar.system.features.render.TargetESP;
import ez.minar.system.features.render.Trails;
import ez.minar.system.features.render.ChinaHat;
import ez.minar.system.features.render.LineGlyphs;
import ez.minar.system.features.render.Waypoints;
import ez.minar.system.features.render.WetWorld;

import ez.minar.system.managers.AltManager;
import ez.minar.system.managers.ConfigManager;
import ez.minar.system.managers.FriendManager;
import ez.minar.system.managers.LanguageSelectManager;
import ez.minar.system.managers.LocalizationManager;
import ez.minar.system.managers.BlockEspManager;
import ez.minar.system.managers.InventoryCleanerManager;
import ez.minar.system.managers.UnhookManager;
import ez.minar.system.autobuy.config.AutoBuyConfigFile;
import ez.minar.system.managers.WaypointManager;
import ez.minar.system.neuro.NeuroManager;
import ez.minar.mixins.interfaces.IRenderPipeline;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.discord.DiscordRichPresence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gl.RenderPipelines;

public class Minar implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DiscordRichPresence.start();
        ((IRenderPipeline) (Object) RenderPipelines.TRANSLUCENT).minar$setWriteDepth(false);
        ThemeManager.init();
        LanguageSelectManager.init();
        LocalizationManager.init();
        AltManager.init();
        FunctionManager.init();
        FriendManager.init();
        BlockEspManager.init();
        InventoryCleanerManager.init();
        WaypointManager.init();
        ConfigManager.init();
        AutoBuyConfigFile.load();
        NeuroManager.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ConfigManager.tickAutoSave();
            NeuroManager.tick();
            UnhookManager.tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            NeuroManager.shutdown();
            DiscordRichPresence.stop();
            LanguageSelectManager.save();
            ConfigManager.save();
            AutoBuyConfigFile.save();
            FriendManager.save();
            BlockEspManager.save();
            InventoryCleanerManager.save();
            WaypointManager.save();
        });
        MinarClientCommands.init();
        WorldRenderEvents.START_MAIN.register(ShaderSky::renderWorld);
        WorldRenderEvents.END_MAIN.register(Particles::renderWorld);
        WorldRenderEvents.END_MAIN.register(Trails::renderWorld);
        WorldRenderEvents.END_MAIN.register(KillEffect::renderWorld);
        WorldRenderEvents.END_MAIN.register(FireFly::renderWorld);
        WorldRenderEvents.END_MAIN.register(JumpCircles::renderWorld);
        WorldRenderEvents.END_MAIN.register(TargetESP::renderWorld);
        WorldRenderEvents.END_MAIN.register(BlockOverlay::renderWorld);
        WorldRenderEvents.END_MAIN.register(BlockESP::renderWorld);
        WorldRenderEvents.END_MAIN.register(Predictions::renderWorld);
        WorldRenderEvents.END_MAIN.register(EntityESP::renderWorld);
        WorldRenderEvents.END_MAIN.register(HitWave::renderWorld);

        WorldRenderEvents.END_MAIN.register(ChinaHat::renderWorld);
        WorldRenderEvents.END_MAIN.register(LineGlyphs::renderWorld);
        WorldRenderEvents.END_MAIN.register(Waypoints::renderWorld);
        WorldRenderEvents.END_MAIN.register(WetWorld::renderWorld);
        WorldRenderEvents.END_MAIN.register(ez.minar.system.features.movement.Blink::renderWorld);
        WorldRenderEvents.END_MAIN.register(ez.minar.system.features.combat.AutoClicker::renderWorld);
        WorldRenderEvents.END_MAIN.register(ez.minar.system.features.combat.AutoCrystal::renderWorld);

        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineRenderState) ->
                BlockOverlay.shouldRenderVanillaBlockOutline());

        EventBus.register(EventManager.getInstance());
        EventBus.register(Waypoints.Instance);
    }
}
