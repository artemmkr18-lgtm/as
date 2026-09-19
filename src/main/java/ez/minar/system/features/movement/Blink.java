package ez.minar.system.features.movement;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.menu.ThemeManager;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.ColorSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.helpers.NetworkUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@NewFunction(name = "Blink", desc = "Задерживает отправку пакетов перемещения, создавая иллюзию телепортации", category = Category.MOVEMENT)
public class Blink extends Function {
    public static Blink Instance;

    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_concrete.png");

    private static final RenderPipeline BLINK_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("minar", "blink_lines"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    public static final RenderLayer BLINK_LINES = RenderLayer.of("minar_blink_lines",
            RenderSetup.builder(BLINK_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private final BooleanSetting render = new BooleanSetting("Отображать позицию", true);
    private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
    private final ColorSetting boxColor = new ColorSetting("Цвет", new Color(168, 85, 247));
    private final NumberSetting lineWidth = new NumberSetting("Толщина линий", 2.0, 0.5, 5.0, 0.1);
    private final NumberSetting fillAlpha = new NumberSetting("Прозрачность заливки", 0.25, 0.0, 1.0, 0.05);

    private final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
    private Box box;
    public static int tickStop = -1;

    public Blink() {
        Instance = this;
        addSettings(render, themeColor, boxColor, lineWidth, fillAlpha);
        themeColor.runnable(this::updateVisibility);
        updateVisibility();
    }

    private void updateVisibility() {
        boxColor.setVisible(!themeColor.isEnabled());
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            box = mc.player.getBoundingBox();
        }
        tickStop = -1;
    }

    @Override
    public void onDisable() {
        flushPackets();
        box = null;
        tickStop = -1;
    }

    private void flushPackets() {
        if (packets.isEmpty()) return;
        for (Packet<?> packet : packets) {
            NetworkUtils.sendSilentPacket(packet);
        }
        packets.clear();
    }

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck.all()) return;

        tickStop--;
        if (tickStop >= 0 && !packets.isEmpty()) {
            if (mc.player != null) {
                box = mc.player.getBoundingBox();
            }
            flushPackets();
        }
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (nullCheck.all()) return;
        if (!isEnabled()) return;

        if (event.getPacket() instanceof ClientStatusC2SPacket status
                && status.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) {
            forceDisable();
            return;
        }

        if (tickStop < 0) {
            packets.add(event.getPacket());
            event.cancel();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        if (nullCheck.all()) return;
        if (!isEnabled()) return;

        if (event.getPacket() instanceof PlayerRespawnS2CPacket
                || event.getPacket() instanceof GameJoinS2CPacket) {
            forceDisable();
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        if (Instance == null || !Instance.isEnabled()) return;
        Instance.renderBox(context);
    }

    private void renderBox(WorldRenderContext context) {
        if (!render.isEnabled() || box == null || mc.world == null || mc.player == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        Box renderBox = box.offset(-camera.x, -camera.y, -camera.z);

        Color c = themeColor.isEnabled() ? ThemeManager.getThemeColor() : boxColor.getColor();
        float alpha = (float) fillAlpha.getValue();
        float lineW = (float) lineWidth.getValue();

        MatrixStack.Entry entry = context.matrices().peek();

        // 1. Box Fill
        if (alpha > 0.001f) {
            VertexConsumer fillBuffer = context.consumers().getBuffer(
                    ez.minar.utils.render.Render3DUtils.texturedLayer(WHITE_TEXTURE));
            Color fillColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (Math.clamp(alpha, 0f, 1f) * 255f));
            drawBoxFill(fillBuffer, entry, renderBox, fillColor);
        }

        // 2. Box Outline
        if (lineW > 0.01f) {
            VertexConsumer lineBuffer = context.consumers().getBuffer(BLINK_LINES);
            Color outlineColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
            drawBoxOutline(lineBuffer, entry, renderBox, outlineColor, lineW);
        }
    }

    private void drawBoxFill(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color) {
        quad(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ);
        quad(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        quad(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
        quad(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.minY, box.maxZ);
        quad(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, box.minX, box.minY, box.minZ);
        quad(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
    }

    private void quad(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      double x3, double y3, double z3,
                      double x4, double y4, double z4) {
        vertex(buffer, entry, color, x1, y1, z1, 0.0f, 1.0f);
        vertex(buffer, entry, color, x2, y2, z2, 1.0f, 1.0f);
        vertex(buffer, entry, color, x3, y3, z3, 1.0f, 0.0f);
        vertex(buffer, entry, color, x4, y4, z4, 0.0f, 0.0f);
    }

    private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                        double x, double y, double z, float u, float v) {
        buffer.vertex(entry, (float) x, (float) y, (float) z)
                .texture(u, v)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private void drawBoxOutline(VertexConsumer buffer, MatrixStack.Entry entry, Box box, Color color, float width) {
        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, width);

        line(buffer, entry, color, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, width);

        line(buffer, entry, color, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, width);
        line(buffer, entry, color, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, width);
        line(buffer, entry, color, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, width);
    }

    private void line(VertexConsumer buffer, MatrixStack.Entry entry, Color color,
                      double x1, double y1, double z1, double x2, double y2, double z2, float width) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0001F) return;

        buffer.vertex(entry, (float) x1, (float) y1, (float) z1)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
        buffer.vertex(entry, (float) x2, (float) y2, (float) z2)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .normal(entry, dx / length, dy / length, dz / length)
                .lineWidth(width);
    }

    public List<Packet<?>> getPackets() {
        return packets;
    }

    public Box getBlinkBox() {
        return box;
    }
}
