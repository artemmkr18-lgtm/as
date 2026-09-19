package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.InputEvent;
import ez.minar.system.events.impl.PacketReceiveEvent;
import ez.minar.system.events.impl.PacketSendEvent;
import ez.minar.system.events.impl.Render2DEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.NumberSetting;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.msdf.Msdf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Locale;

@NewFunction(name = "FreeCam", desc = "Позволяет свободно летать камерой по миру", category = Category.MISC)
public class FreeCam extends Function {
    private final NumberSetting speed = new NumberSetting("Скорость", 2.0, 0.5, 5.0, 0.1);
    private final BooleanSetting freeze = new BooleanSetting("Заморозка", false);

    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prevPos = Vec3d.ZERO;
    private String displayedCoords = "";
    private long coordsChangeTime;

    public FreeCam() {
        addSettings(speed, freeze);
    }

    public static FreeCam getInstance() {
        return FunctionManager.getFunction(FreeCam.class);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            return;
        }

        pos = prevPos = mc.player.getCameraPosVec(1.0F);
        displayedCoords = formatCoords(pos);
        coordsChangeTime = System.currentTimeMillis();
        mc.chunkCullingEnabled = false;
    }

    @Override
    public void onDisable() {
        mc.chunkCullingEnabled = true;
    }

    @EventHandler
    public void onSend(PacketSendEvent event) {
        if (freeze.isEnabled() && event.getPacket() instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }

    @EventHandler
    public void onReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerRespawnS2CPacket || event.getPacket() instanceof GameJoinS2CPacket) {
            toggle();
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null) {
            return;
        }

        float forward = event.getForward();
        float strafe = event.getStrafe();
        double step = speed.getValue();

        prevPos = pos;
        Vec3d horizontal = movementVector(forward, strafe, mc.player.getYaw()).multiply(step);
        double y = event.isJump() ? step : event.isSneak() ? -step : 0.0;
        pos = pos.add(horizontal.x, y, horizontal.z);

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (event.getContext() == null || mc.player == null) {
            return;
        }

        float tickDelta = event.getTickCounter().getTickProgress(true);
        Vec3d cameraPos = new Vec3d(getCameraX(tickDelta), getCameraY(tickDelta), getCameraZ(tickDelta));
        String coords = formatCoords(cameraPos);
        if (!coords.equals(displayedCoords)) {
            displayedCoords = coords;
            coordsChangeTime = System.currentTimeMillis();
        }

        float progress = Math.clamp((System.currentTimeMillis() - coordsChangeTime) / 260.0F, 0.0F, 1.0F);
        float bounce = (float) Math.sin(progress * Math.PI) * 2.2F;
        float size = 8.5F + bounce;
        float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        float y = mc.getWindow().getScaledHeight() / 2.0F + 13.0F;

        RenderUtil.text(event.getContext(), Msdf.SF_BOLD, centerX + 1.0F, y + 1.0F, displayedCoords, size, new Color(0, 0, 0, 140), "center");
        RenderUtil.text(event.getContext(), Msdf.SF_BOLD, centerX, y, displayedCoords, size, new Color(245, 247, 250), "center");
    }

    public double getCameraX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.x, pos.x);
    }

    public double getCameraY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.y, pos.y);
    }

    public double getCameraZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPos.z, pos.z);
    }

    public float getCameraYaw(float tickDelta) {
        if (mc.player == null) {
            return 0.0F;
        }

        return MathHelper.lerpAngleDegrees(tickDelta, mc.player.lastYaw, mc.player.getYaw());
    }

    public float getCameraPitch(float tickDelta) {
        if (mc.player == null) {
            return 0.0F;
        }

        return MathHelper.lerp(tickDelta, mc.player.lastPitch, mc.player.getPitch());
    }

    private Vec3d movementVector(float forward, float strafe, float yaw) {
        if (forward == 0.0F && strafe == 0.0F) {
            return Vec3d.ZERO;
        }

        double angle = Math.toRadians(yaw);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        return new Vec3d(x, 0.0, z).normalize();
    }

    private String formatCoords(Vec3d value) {
        return String.format(Locale.ROOT, "X %.1f  Z %.1f  Y %.1f", value.x, value.z, value.y);
    }
}
