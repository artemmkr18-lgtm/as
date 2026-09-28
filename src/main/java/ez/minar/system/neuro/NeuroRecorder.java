package ez.minar.system.neuro;

import ez.minar.system.managers.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class NeuroRecorder {
    private static final String CSV_HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint\n";
    private static final int FLUSH_INTERVAL = 256;
    private static final int POST_TARGET_TICKS = 40;
    private static final int ATTACK_FOCUS_TICKS = 60;
    private static final double MAX_TARGET_DIST = 5.0D;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private BufferedWriter writer;
    private Path activeFile;
    private String sessionName = "session";
    private boolean recording;

    private int tickCount;
    private int recordedTicks;
    private int unwrittenTicks;
    private int postTargetCounter;

    private int lastAttackedId = -1;
    private int lastAttackedTick = -1;
    private boolean attackFlag;

    private boolean hasLastRot;
    private float lastYaw;
    private float lastPitch;

    public synchronized String start(String name) {
        if (this.recording) {
            return "Запись уже идёт: " + this.sessionName;
        }
        try {
            Path dir = NeuroModel.getDataDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".csv");
            boolean isNew = !Files.isRegularFile(file) || Files.size(file) == 0L;

            this.writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            if (isNew) {
                this.writer.write(CSV_HEADER);
                this.writer.flush();
            }

            this.activeFile = file;
            this.sessionName = name;
            this.recording = true;
            this.tickCount = 0;
            this.recordedTicks = 0;
            this.unwrittenTicks = 0;
            this.postTargetCounter = 0;
            this.lastAttackedId = -1;
            this.lastAttackedTick = -1;
            this.hasLastRot = false;
            this.attackFlag = false;

            return "Начата запись датасета: " + name;
        } catch (Exception e) {
            this.recording = false;
            this.writer = null;
            return "Ошибка старта записи: " + e.getMessage();
        }
    }

    public synchronized String stop() {
        if (!this.recording) {
            return "Запись не активна";
        }
        this.recording = false;
        try {
            if (this.writer != null) {
                this.writer.flush();
                this.writer.close();
            }
        } catch (Exception ignored) {
        }
        this.writer = null;
        return "Запись остановлена: " + this.sessionName + " (" + this.recordedTicks + " тиков)";
    }

    public boolean isRecording() {
        return this.recording;
    }

    public String getSessionName() {
        return this.sessionName;
    }

    public int getRecordedTicks() {
        return this.recordedTicks;
    }

    public synchronized void onAttack(Entity entity) {
        if (!this.recording || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (this.mc.player != null && living != this.mc.player) {
            this.lastAttackedId = living.getId();
            this.lastAttackedTick = this.tickCount;
            this.attackFlag = true;
        }
    }

    public synchronized void tick() {
        if (!this.recording || this.mc.player == null || this.mc.world == null || this.writer == null) {
            return;
        }

        this.tickCount++;

        float curYaw = this.mc.player.getYaw();
        float curPitch = this.mc.player.getPitch();
        float deltaYaw = this.hasLastRot ? MathHelper.wrapDegrees(curYaw - this.lastYaw) : 0.0F;
        float deltaPitch = this.hasLastRot ? (curPitch - this.lastPitch) : 0.0F;
        this.lastYaw = curYaw;
        this.lastPitch = curPitch;
        this.hasLastRot = true;

        LivingEntity target = findTarget();
        if (target == null) {
            if (this.postTargetCounter-- > 0) {
                writeRow(curYaw, curPitch, deltaYaw, deltaPitch, null);
            }
            this.attackFlag = false;
        } else {
            this.postTargetCounter = POST_TARGET_TICKS;
            writeRow(curYaw, curPitch, deltaYaw, deltaPitch, target);
        }
    }

    private void writeRow(float yaw, float pitch, float dyaw, float dpitch, LivingEntity target) {
        try {
            float gcd = calculateGcd();
            int clean = 1;
            int has = target != null ? 1 : 0;
            int tid = target != null ? target.getId() : -1;

            Vec3d eyes = this.mc.player.getEyePos();
            Box box = target != null ? target.getBoundingBox() : null;
            Vec3d rel = box != null ? box.getCenter().subtract(eyes) : Vec3d.ZERO;

            double bw = box != null ? box.getLengthX() : 0.0D;
            double bh = box != null ? box.getLengthY() : 0.0D;
            double dist = box != null ? distanceToBox(eyes, box) : -1.0D;

            int vis = (target != null && hasLineOfSight(target)) ? 1 : 0;
            int on = (box != null && isAimingAtBox(yaw, pitch, box)) ? 1 : 0;
            int atk = this.attackFlag ? 1 : 0;
            this.attackFlag = false;

            float hp = target != null ? target.getHealth() : -1.0F;
            int ground = this.mc.player.isOnGround() ? 1 : 0;
            int sprint = this.mc.player.isSprinting() ? 1 : 0;

            String row = String.format(Locale.ROOT,
                    "%d,%.6f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.1f,%d,%d\n",
                    this.tickCount, gcd, clean, yaw, pitch, dyaw, dpitch, has, tid,
                    rel.x, rel.y, rel.z, bw, bh, dist, vis, on, atk, hp, ground, sprint
            );

            this.writer.write(row);
            this.recordedTicks++;
            if (++this.unwrittenTicks >= FLUSH_INTERVAL) {
                this.writer.flush();
                this.unwrittenTicks = 0;
            }
        } catch (Exception ignored) {
        }
    }

    private LivingEntity findTarget() {
        if (this.mc.world == null || this.mc.player == null) {
            return null;
        }
        Vec3d eye = this.mc.player.getEyePos();

        if (this.lastAttackedId >= 0 && (this.tickCount - this.lastAttackedTick) <= ATTACK_FOCUS_TICKS) {
            Entity entity = this.mc.world.getEntityById(this.lastAttackedId);
            if (entity instanceof LivingEntity living && living.isAlive() && distanceToBox(eye, living.getBoundingBox()) <= MAX_TARGET_DIST) {
                return living;
            }
        }

        LivingEntity closest = null;
        double closestDist = MAX_TARGET_DIST;
        for (Entity entity : this.mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == this.mc.player || !living.isAlive()) {
                continue;
            }
            double d = distanceToBox(eye, living.getBoundingBox());
            if (d <= closestDist && hasLineOfSight(living)) {
                closest = living;
                closestDist = d;
            }
        }
        return closest;
    }

    private boolean hasLineOfSight(LivingEntity entity) {
        if (this.mc.player == null || this.mc.world == null) {
            return false;
        }
        Vec3d start = this.mc.player.getEyePos();
        Vec3d end = entity.getBoundingBox().getCenter();
        RaycastContext ctx = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player);
        return this.mc.world.raycast(ctx).getType() == HitResult.Type.MISS;
    }

    private boolean isAimingAtBox(float yaw, float pitch, Box box) {
        if (this.mc.player == null) {
            return false;
        }
        Vec3d eye = this.mc.player.getEyePos();
        float yawRad = (float) Math.toRadians(-yaw - 90.0F);
        float pitchRad = (float) Math.toRadians(-pitch);
        double cosPitch = Math.cos(pitchRad);
        Vec3d look = new Vec3d(Math.cos(yawRad) * cosPitch, Math.sin(pitchRad), Math.sin(yawRad) * cosPitch);
        Vec3d reachEnd = eye.add(look.multiply(6.0D));
        return box.raycast(eye, reachEnd).isPresent();
    }

    private float calculateGcd() {
        if (this.mc.options == null) {
            return 0.15F;
        }
        double sens = this.mc.options.getMouseSensitivity().getValue();
        double d2 = sens * 0.6D + 0.2D;
        return (float) (d2 * d2 * d2 * 1.2D);
    }

    public static double distanceToBox(Vec3d eye, Box box) {
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0D), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0D), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0D), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
