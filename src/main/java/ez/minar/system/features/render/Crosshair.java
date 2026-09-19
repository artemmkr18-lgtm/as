package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

@NewFunction(name = "Crosshair", desc = "\u041a\u0430\u0441\u0442\u043e\u043c\u043d\u044b\u0439 \u043f\u0440\u0438\u0446\u0435\u043b", category = Category.RENDER)
public class Crosshair extends Function {
    public static Crosshair Instance;

    private final NumberSetting length = new NumberSetting("\u0414\u043b\u0438\u043d\u0430", 6.0, 1.0, 20.0, 0.5);
    private final NumberSetting width = new NumberSetting("\u0428\u0438\u0440\u0438\u043d\u0430", 2.0, 1.0, 6.0, 0.5);
    private final NumberSetting gap = new NumberSetting("\u041c\u0435\u0441\u0442\u043e \u043f\u043e \u0441\u0435\u0440\u0435\u0434\u0438\u043d\u0435", 3.0, 0.0, 12.0, 0.5);
    private final BooleanSetting outline = new BooleanSetting("\u041e\u0431\u0432\u043e\u0434\u043a\u0430", true);
    private final ColorSetting color = new ColorSetting("\u0426\u0432\u0435\u0442", new Color(255, 255, 255));
    private final BooleanSetting showCooldown = new BooleanSetting("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c \u043a\u0443\u043b\u0434\u0430\u0443\u043d", true);
    private final NumberSetting cooldownStretch = new NumberSetting("\u0420\u0430\u0441\u0442\u044f\u0436\u0435\u043d\u0438\u0435 \u043a\u0443\u043b\u0434\u0430\u0443\u043d\u0430", 8.0, 0.0, 24.0, 0.5);

    private float animatedCooldown;
    private long lastFrameNanos;

    public Crosshair() {
        Instance = this;
        addSettings(length, width, gap, outline, color, showCooldown, cooldownStretch);
    }

    @Override
    public void onDisable() {
        animatedCooldown = 0f;
        lastFrameNanos = 0L;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!showCooldown.isEnabled() || event.getPlayer() != mc.player) return;
        animatedCooldown = Math.max(animatedCooldown, (float) cooldownStretch.getValue());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        DrawContext context = event.getContext();
        if (context == null || mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        if (mc.currentScreen != null) return;

        updateCooldown();

        float centerX = RenderUtil.getFixedScaledWidth() / 2f;
        float centerY = RenderUtil.getFixedScaledHeight() / 2f;
        float lineLength = (float) length.getValue();
        float lineWidth = (float) width.getValue();
        float offset = (float) gap.getValue() + animatedCooldown;

        if (outline.isEnabled()) {
            renderCrosshair(centerX, centerY, lineLength, lineWidth, offset, new Color(0, 0, 0, 180), 1f);
        }

        renderCrosshair(centerX, centerY, lineLength, lineWidth, offset, color.getColor(), 0f);
    }

    private void updateCooldown() {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        float target = 0f;
        if (showCooldown.isEnabled() && mc.player != null) {
            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            float cooldown = mc.player.getAttackCooldownProgress(tickDelta);
            target = (1f - cooldown) * (float) cooldownStretch.getValue();
        }

        float speed = 1f - (float) Math.exp(-18f * deltaSeconds);
        animatedCooldown += (target - animatedCooldown) * speed;

        if (Math.abs(animatedCooldown - target) < 0.01f) {
            animatedCooldown = target;
        }
    }

    private void renderCrosshair(float x, float y, float lineLength, float lineWidth, float offset, Color renderColor, float padding) {
        float halfWidth = lineWidth / 2f;
        float paddedWidth = lineWidth + padding * 2f;
        float paddedLength = lineLength + padding * 2f;

        RenderUtil.rect(x - halfWidth - padding, y - offset - lineLength - padding, paddedWidth, paddedLength, 0f, renderColor);
        RenderUtil.rect(x - halfWidth - padding, y + offset - padding, paddedWidth, paddedLength, 0f, renderColor);
        RenderUtil.rect(x - offset - lineLength - padding, y - halfWidth - padding, paddedLength, paddedWidth, 0f, renderColor);
        RenderUtil.rect(x + offset - padding, y - halfWidth - padding, paddedLength, paddedWidth, 0f, renderColor);
    }

    public static boolean shouldReplaceVanilla() {
        return Instance != null && Instance.isEnabled();
    }
}
