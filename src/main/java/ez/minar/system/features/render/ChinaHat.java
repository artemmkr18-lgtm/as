package ez.minar.system.features.render;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.awt.Color;
import java.lang.reflect.Field;

@NewFunction(name = "ChinaHat", desc = "Китайская шляпа на голове", category = Category.RENDER)
public class ChinaHat extends Function {
    public static ChinaHat Instance;
    public static VertexConsumerProvider.Immediate consumers;

    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
    private final ColorSetting color = new ColorSetting("Цвет", new Color(255, 255, 255));
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность", 0.65, 0.05, 1.0, 0.05);
    private final ModeSetting fillMode = new ModeSetting("Тип заливки", "Обычная", "Full", "WebShader", "Plasma", "ChamsFill", "Waves", "Water");
    private final BooleanSetting outline = new BooleanSetting("Обводка", true);
    private final NumberSetting radius = new NumberSetting("Радиус", 0.8, 0.3, 1.5, 0.05);
    private final NumberSetting height = new NumberSetting("Высота", 0.3, 0.1, 0.8, 0.05);
    private final NumberSetting positionY = new NumberSetting("Позиция Y", 0.0, -0.5, 0.5, 0.05);
    private final BooleanSetting respectDepth = new BooleanSetting("Не видеть сквозь стены", true);

    private static Field fOriginX, fOriginY, fOriginZ, fPitch, fYaw, fRoll;

    public ChinaHat() {
        Instance = this;
        addSettings(themeColor, color, fillAlpha, fillMode, outline, radius, height, positionY, respectDepth);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        color.setVisible(!themeColor.isEnabled());
    }

    private Color selectedColor() {
        Color selected = themeColor.isEnabled() ? ThemeManager.getThemeColor() : color.getColor();
        if (!themeColor.isEnabled()) {
            return selected;
        }
        return new Color(brighten(selected.getRed()), brighten(selected.getGreen()), brighten(selected.getBlue()));
    }

    private static int brighten(int value) {
        return Math.clamp((int) (value + (255 - value) * 0.45f), 0, 255);
    }

    private int shaderModeIndex() {
        if (fillMode.isEnabled("Обычная")) return 10;
        if (fillMode.isEnabled("Full")) return 0;
        if (fillMode.isEnabled("WebShader")) return 2;
        if (fillMode.isEnabled("Plasma")) return 4;
        if (fillMode.isEnabled("ChamsFill")) return 6;
        if (fillMode.isEnabled("Waves")) return 8;
        if (fillMode.isEnabled("Water")) return 12;
        return 10;
    }

    public void render(MatrixStack matrices, BipedEntityModel<?> model) {
        if (consumers == null) {
            consumers = VertexConsumerProvider.immediate(new net.minecraft.client.util.BufferAllocator(786432));
        }

        boolean chamsFill = fillMode.isEnabled("ChamsFill");
        RenderLayer layer;
        if (respectDepth.isEnabled()) {
            layer = chamsFill ? EntityESP.ENTITY_CHAMS_FILL : EntityESP.ENTITY_SHADER;
        } else {
            layer = chamsFill ? BlockOverlay.SEE_THROUGH_CHAMS_FILL : BlockOverlay.SEE_THROUGH_SHADER;
        }
        VertexConsumer fillBuffer = consumers.getBuffer(layer);

        // Uses Glow Box Lines for the outline without depth for full visibility, or with depth depending on preference.
        // We will use no depth for outline so it's always visible like the shader.
        // Actually EntityESP has GLOW_BOX_LINES_NO_DEPTH but it's private. Let's use vanilla line layer or just make it public.
        // For simplicity we can use an emissive line layer or standard line.
        // I will use a custom VertexConsumer lineBuffer if outline is true.
        
        Color renderColor = selectedColor();
        float alpha = (float) fillAlpha.getValue();
        if (alpha <= 0.0f) return;
        
        float modeOffset = chamsFill ? 0.0f : shaderModeIndex() * 2.0f;
        
        matrices.push();
        matrices.scale(0.0625f, 0.0625f, 0.0625f);
        translateAndRotate(matrices, model.head);
        
        // Head center is typically 0, top of head is -8 in part space (because Y grows downwards in model space usually).
        // Wait, standard biped head is minX=-4, minY=-8, minZ=-4, maxX=4, maxY=0, maxZ=4.
        // So top is y = -8.0
        float yPos = -8.0f - (float) (positionY.getValue() * 16.0);
        float h = (float) (height.getValue() * 16.0);
        float r = (float) (radius.getValue() * 16.0);

        MatrixStack.Entry entry = matrices.peek();
        int segments = 30;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * Math.PI * 2.0 / segments);
            float angle2 = (float) ((i + 1) * Math.PI * 2.0 / segments);

            float x1 = (float) Math.cos(angle1) * r;
            float z1 = (float) Math.sin(angle1) * r;
            float x2 = (float) Math.cos(angle2) * r;
            float z2 = (float) Math.sin(angle2) * r;

            float u1 = (x1 / r) * 0.5f + 0.5f;
            float v1 = (z1 / r) * 0.5f + 0.5f + modeOffset;

            float u2 = (x2 / r) * 0.5f + 0.5f;
            float v2 = (z2 / r) * 0.5f + 0.5f + modeOffset;

            float u3 = 0.5f;
            float v3 = 0.5f + modeOffset;

            // Draw quad for fill (two vertices at the base, two at the top to make a quad, but top is a point)
            quadShader(fillBuffer, entry, renderColor, 
                       x1, yPos, z1, u1, v1,
                       x2, yPos, z2, u2, v2,
                       0, yPos - h, 0, u3, v3,
                       0, yPos - h, 0, u3, v3,
                       alpha);
            
            // Draw inner quad (backface)
            quadShader(fillBuffer, entry, renderColor, 
                       x2, yPos, z2, u2, v2,
                       x1, yPos, z1, u1, v1,
                       0, yPos - h, 0, u3, v3,
                       0, yPos - h, 0, u3, v3,
                       alpha);
        }

        matrices.pop();
        
        // Draw Outline
        if (outline.isEnabled()) {
            VertexConsumer lineBuffer = consumers.getBuffer(respectDepth.isEnabled() ? EntityESP.GLOW_BOX_LINES : EntityESP.GLOW_BOX_LINES_NO_DEPTH);
            matrices.push();
            matrices.scale(0.0625f, 0.0625f, 0.0625f);
            translateAndRotate(matrices, model.head);
            entry = matrices.peek();
            
            for (int i = 0; i < segments; i++) {
                float angle1 = (float) (i * Math.PI * 2.0 / segments);
                float angle2 = (float) ((i + 1) * Math.PI * 2.0 / segments);

                float x1 = (float) Math.cos(angle1) * r;
                float z1 = (float) Math.sin(angle1) * r;
                float x2 = (float) Math.cos(angle2) * r;
                float z2 = (float) Math.sin(angle2) * r;

                // Base circle line
                line(lineBuffer, entry, renderColor, x1, yPos, z1, x2, yPos, z2, 2.0f, 1.0f);
                // Lines to top
                if (i % 5 == 0) {
                    line(lineBuffer, entry, renderColor, x1, yPos, z1, 0, yPos - h, 0, 2.0f, 1.0f);
                }
            }
            matrices.pop();
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        if (consumers != null) {
            consumers.draw();
        }
    }

    private void quadShader(VertexConsumer buffer, MatrixStack.Entry entry, Color color, 
                            float x1, float y1, float z1, float u1, float v1,
                            float x2, float y2, float z2, float u2, float v2,
                            float x3, float y3, float z3, float u3, float v3,
                            float x4, float y4, float z4, float u4, float v4,
                            float alpha) {
        vertexShader(buffer, entry, color, x1, y1, z1, u1, v1, alpha);
        vertexShader(buffer, entry, color, x2, y2, z2, u2, v2, alpha);
        vertexShader(buffer, entry, color, x3, y3, z3, u3, v3, alpha);
        vertexShader(buffer, entry, color, x4, y4, z4, u4, v4, alpha);
    }

    private void vertexShader(VertexConsumer buffer, MatrixStack.Entry entry, Color color, float x, float y, float z, float u, float v, float alpha) {
        buffer.vertex(entry, x, y, z).texture(u, v).color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color, double x1, double y1, double z1, double x2, double y2, double z2, float width, float alpha) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0E-4f) return;

        buffer.vertex(entry, (float) x1, (float) y1, (float) z1).color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0.0f, 1.0f) * 255.0f)).normal(entry, dx / length, dy / length, dz / length).lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2).color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.clamp(alpha, 0.0f, 1.0f) * 255.0f)).normal(entry, dx / length, dy / length, dz / length).lineWidth(width);
    }

    private void translateAndRotate(MatrixStack matrices, ModelPart part) {
        matrices.translate(getF(part, fOriginX), getF(part, fOriginY), getF(part, fOriginZ));
        float r = getF(part, fRoll);
        if (r != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotation(r));
        float y = getF(part, fYaw);
        if (y != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotation(y));
        float p = getF(part, fPitch);
        if (p != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotation(p));
    }

    private float getF(ModelPart part, Field f) {
        try {
            return f != null ? f.getFloat(part) : 0.0f;
        } catch (Exception e) {
            return 0.0f;
        }
    }

    private static Field getField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Exception ignored) {}
        }
        return null;
    }

    static {
        fOriginX = getField(ModelPart.class, "originX", "pivotX", "x");
        fOriginY = getField(ModelPart.class, "originY", "pivotY", "y");
        fOriginZ = getField(ModelPart.class, "originZ", "pivotZ", "z");
        fPitch = getField(ModelPart.class, "pitch", "xRot");
        fYaw = getField(ModelPart.class, "yaw", "yRot");
        fRoll = getField(ModelPart.class, "roll", "zRot");
    }
}
