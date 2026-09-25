package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.AttackEntityEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.Render3DUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@NewFunction(name = "HitBubbles", desc = "Светящиеся вихри колец в точке удара", category = Category.RENDER)
public class HitBubbles extends Function {
    private static final Identifier BUBBLE_TEXTURE = Identifier.of("minar", "images/particles/bubble.png");
    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static final float BASE_ALPHA = 0.85f;

    public static HitBubbles Instance;

    private final NumberSetting size = new NumberSetting("Размер", 0.9, 0.3, 2.5, 0.05);
    private final NumberSetting duration = new NumberSetting("Длительность", 3.0, 0.5, 6.0, 0.1);
    private final NumberSetting turns = new NumberSetting("Оборотов", 1.5, 0.0, 6.0, 0.1);
    private final BooleanSetting themeColor = new BooleanSetting("Theme color", true);
    private final ColorSetting color = new ColorSetting("Color", Color.WHITE);

    public HitBubbles() {
        Instance = this;
        addSettings(size, duration, turns, themeColor, color);
        themeColor.runnable(() -> color.setVisible(!themeColor.isEnabled()));
        color.setVisible(false);
    }

    @Override
    public void onDisable() {
        BUBBLES.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        Vec3d position = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);
        BUBBLES.add(new Bubble(position, (float) (Math.random() * 360.0), System.nanoTime()));
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled() || BUBBLES.isEmpty()) return;
        Instance.render(context);
    }

    private void render(WorldRenderContext context) {
        long now = System.nanoTime();
        long lifeNanos = (long) (duration.getValue() * 1_000_000_000.0);
        float baseSize = (float) size.getValue();
        float spinTurns = (float) turns.getValue();
        Color selected = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();

        Iterator<Bubble> iterator = BUBBLES.iterator();
        Render3DUtils.TexturedBillboardBatch batch =
                Render3DUtils.additiveTexturedBillboardBatch(context, BUBBLE_TEXTURE);
        while (iterator.hasNext()) {
            Bubble bubble = iterator.next();
            float age = (now - bubble.spawnedAt()) / (float) lifeNanos;
            if (age >= 1f) {
                iterator.remove();
                continue;
            }

            float grow = smooth(Math.clamp(age / 0.22f, 0f, 1f)) * (0.45f + 0.55f * age);
            float fade = smooth(Math.clamp((1f - age) / 0.3f, 0f, 1f));
            batch.render(bubble.position(), baseSize * grow, bubble.rotation() + age * 360f * spinTurns,
                    selected.getRed(), selected.getGreen(), selected.getBlue(), BASE_ALPHA * fade);
        }
    }

    private static float smooth(float value) {
        return value * value * (3f - 2f * value);
    }

    private record Bubble(Vec3d position, float rotation, long spawnedAt) {}
}
