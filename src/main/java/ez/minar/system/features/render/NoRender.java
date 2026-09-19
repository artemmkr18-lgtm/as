package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.BooleanSetting;

@NewFunction(name = "NoRender", desc = "\u041E\u0442\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u044B\u0435 \u0432\u0438\u0437\u0443\u0430\u043B\u044C\u043D\u044B\u0435 \u044D\u0444\u0444\u0435\u043A\u0442\u044B", category = Category.RENDER)
public class NoRender extends Function {
    public static NoRender Instance;

    private final BooleanSetting vignette = new BooleanSetting("\u0412\u0438\u043D\u044C\u0435\u0442\u043A\u0430", true);
    private final BooleanSetting badEffects = new BooleanSetting("\u041F\u043B\u043E\u0445\u0438\u0435 \u044D\u0444\u0444\u0435\u043A\u0442\u044B", true);
    private final BooleanSetting fireOverlay = new BooleanSetting("\u041E\u0433\u043E\u043D\u044C \u043D\u0430 \u0432\u0435\u0441\u044C \u044D\u043A\u0440\u0430\u043D", true);
    private final BooleanSetting hurtCamera = new BooleanSetting("\u0422\u0440\u044F\u0441\u043A\u0430 \u043A\u0430\u043C\u0435\u0440\u044B", true);
    private final BooleanSetting crosshair = new BooleanSetting("\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u0438\u0446\u0435\u043B", false);
    private final BooleanSetting worldArrows = new BooleanSetting("3D \u0441\u0442\u0440\u0435\u043B\u043A\u0438 \u0432 \u043C\u0438\u0440\u0435", false);

    public NoRender() {
        Instance = this;
        addSettings(vignette, badEffects, fireOverlay, hurtCamera, crosshair, worldArrows);
    }

    public static boolean shouldDisableVignette() {
        return Instance != null && Instance.isEnabled() && Instance.vignette.isEnabled();
    }

    public static boolean shouldDisableBadEffects() {
        return Instance != null && Instance.isEnabled() && Instance.badEffects.isEnabled();
    }

    public static boolean shouldDisableFireOverlay() {
        return Instance != null && Instance.isEnabled() && Instance.fireOverlay.isEnabled();
    }

    public static boolean shouldDisableHurtCamera() {
        return Instance != null && Instance.isEnabled() && Instance.hurtCamera.isEnabled();
    }

    public static boolean shouldDisableCrosshair() {
        return Instance != null && Instance.isEnabled() && Instance.crosshair.isEnabled();
    }

    public static boolean shouldDisableWorldArrows() {
        return Instance != null && Instance.isEnabled() && Instance.worldArrows.isEnabled();
    }
}
