package ez.minar.system.api;

import ez.minar.system.features.combat.*;
import ez.minar.system.features.misc.*;
import ez.minar.system.features.movement.*;
import ez.minar.system.features.player.*;
import ez.minar.system.features.render.*;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionManager {
    @Getter private static final List<Function> functions = new ArrayList<>();

    public static void init() {
        register(new AttackAura(), new AutoCrystal(), new AutoClicker(), new MaceKiller(), new TargetStrafe(), new AntiBot(), new AutoTotem(), new AutoArmor(), new AutoGApple(), new NoFriendDamage(), new TriggerBot(), new AutoSwap(), new Velocity(), new AutoPotion(), new SpamCrossbow());

        register(new Sprint(), new Speed(), new NoSlow(), new NoPush(), new Fly(), new AirStuck(), new ElytraFly(), new NoWeb(), new GuiMove(), new Scaffold(), new Spider(), new Blink());

        register(new ClickGuiSettings(), new HUD(), new Crosshair(), new Particles(), new Trails(), new KillEffect(), new FireFly(), new JumpCircles(), new TargetESP(), new BlockOverlay(), new BlockESP(), new Predictions(), new ShaderSky(), new SwingAnimations(), new ViewModel(), new BeautifulHands(), new HandChams(), new HandShader(), new EntityESP(), new SkeletonESP(), new Fog(), new NoRender(), new Arrows(), new HitWave(), new HitBubbles(), new ImpactRing(), new SmoothCamera(), new FullBright(), new WorldRecolor(), new AntiInvisible(), new NameTags(), new ChinaHat(), new Optimization(), new WetWorld(), new LineGlyphs(), new MotionBlur(), new Atmosphere(), new Chams());

        register(new NoDelay(), new AutoDuels(), new ElytraHelper(), new SwapSetting(), new ClickAction(), new NoInteract(), new ItemScroller(), new AutoTool(), new TapeMouse(), new NoSlotChange(), new InventoryCleaner(), new ChestStealer(), new AhHelper());

        register(new Sounds(), new Ambience(), new AntiAFK(), new AutoTpAccept(), new FreeCam(), new ServerRPSpoofer(), new StreamerMode(), new AutoRespawn(), new AutoAuth(), new ChatHelper(), new ChatTracker(), new Spammer(), new Cosmetics(),
                new PotionCombiner(), new CreeperFarm(), new BaseFinder(), new AutoCraft(), new CocoaFarm(),
                new AutoSell(), new MoneyFarm(), new WardenFarm(),
                new AppleFarmer(), new AutoWood(), new AutoMine(), new AutoVillageTrade(), new ChorusFarm(), new AutoBuy(), new Unhook());


        getFunction(HUD.class).forceEnable();
    }

    private static void register(Function... functionArray) {
        functions.addAll(Arrays.asList(functionArray));
    }

    @SuppressWarnings("unchecked")
    public static <T extends Function> T getFunction(Class<T> clazz) {
        return (T) functions.stream()
                .filter(f -> f.getClass() == clazz)
                .findFirst()
                .orElse(null);
    }

    public static List<Function> getFunctionsByCategory(Category category) {
        return functions.stream()
                .filter(f -> f.getCategory() == category)
                .collect(Collectors.toList());
    }
}
