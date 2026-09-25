package ez.minar.utils.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Shockwave rings: a wave quad that refracts a copy of the scene, plus a noise driven glow curtain
 * around the growing radius. Vertices are baked into view space on the CPU, so the vertex shader
 * only needs the projection.
 */
public final class ImpactRingPipeline {
    private static final int GLOW_SEGMENTS = 48;
    private static final float GLOW_HEIGHT = 1.0f;
    private static final int WAVE_VERTICES = 6;
    private static final int GLOW_VERTICES = GLOW_SEGMENTS * 6;
    private static final int BYTES_PER_VERTEX = 20;
    private static final int UNIFORM_SIZE = 96;
    private static final int VERTEX_CAPACITY = GLOW_VERTICES * BYTES_PER_VERTEX;

    private static final RenderPipeline WAVE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "impact_wave"))
                    .withVertexShader(Identifier.of("minar", "impact_vertex"))
                    .withFragmentShader(Identifier.of("minar", "impact_wave_fragment"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.of("minar", "impact_glow"))
                    .withVertexShader(Identifier.of("minar", "impact_vertex"))
                    .withFragmentShader(Identifier.of("minar", "impact_glow_fragment"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static GpuTexture sceneTexture;
    private static GpuTextureView sceneView;
    private static int sceneWidth;
    private static int sceneHeight;

    private static GpuBuffer vertexBuffer;
    private static GpuBuffer uniformBuffer;
    private static ByteBuffer vertexData;
    private static ByteBuffer uniformData;

    private static CommandEncoder encoder;
    private static Framebuffer framebuffer;
    private static Matrix4f storedProjection;
    private static final Vector3f VIEW_POINT = new Vector3f();
    private static final Vector3d CAMERA_POSITION = new Vector3d();
    private static final Quaternionf WORLD_TO_VIEW = new Quaternionf();

    private ImpactRingPipeline() {
    }

    /** Copies the scene once per frame. Must be called before any draw of this frame. */
    public static boolean beginFrame(Matrix4f projection) {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null || fb.getDepthAttachment() == null) return false;
        if (fb.textureWidth <= 0 || fb.textureHeight <= 0) return false;
        if (projection == null) return false;

        ensureSceneTexture(fb.textureWidth, fb.textureHeight);
        ensureBuffers();
        if (sceneView == null || vertexBuffer == null || uniformBuffer == null) return false;

        encoder = RenderSystem.getDevice().createCommandEncoder();
        framebuffer = fb;
        storedProjection = projection;
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneTexture,
                0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight);
        return true;
    }

    public static void drawWave(Vec3d center, Vec3d axisU, Vec3d axisV, float radius,
                                float progress, float strength, float fade, Color color) {
        if (encoder == null || radius <= 0.001f || strength <= 0.001f || fade <= 0.003f) return;
        int vertices = writeQuad(center, axisU, axisV, radius);
        upload(progress, strength, fade, 0f, color);
        submit(WAVE_PIPELINE, vertices);
    }

    public static void drawGlow(Vec3d center, Vec3d axisU, Vec3d axisV, float radius,
                                float progress, float fade, float time, Color color) {
        if (encoder == null || radius <= 0.001f || fade <= 0.003f) return;
        int vertices = writeCurtain(center, axisU, axisV, Math.max(0.001f, radius * progress));
        upload(fade, time, 0f, progress, color);
        submit(GLOW_PIPELINE, vertices);
    }

    private static void submit(RenderPipeline pipeline, int vertices) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "minar:impact",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("Uniforms", uniformBuffer);
            pass.bindTexture("Sampler0", sceneView, sampler);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertices);
        }
    }

    private static int writeQuad(Vec3d center, Vec3d axisU, Vec3d axisV, float radius) {
        Vec3d u = axisU.multiply(radius);
        Vec3d v = axisV.multiply(radius);
        beginGeometry();
        put(center.add(u).add(v), 1f, 1f);
        put(center.add(u).subtract(v), 1f, 0f);
        put(center.subtract(u).subtract(v), 0f, 0f);
        put(center.add(u).add(v), 1f, 1f);
        put(center.subtract(u).subtract(v), 0f, 0f);
        put(center.subtract(u).add(v), 0f, 1f);
        return WAVE_VERTICES;
    }

    private static int writeCurtain(Vec3d center, Vec3d axisU, Vec3d axisV, float radius) {
        Vec3d u = axisU.normalize();
        Vec3d v = axisV.normalize();
        Vec3d normal = u.crossProduct(v).normalize().multiply(GLOW_HEIGHT);
        beginGeometry();
        for (int i = 0; i < GLOW_SEGMENTS; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / GLOW_SEGMENTS);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / GLOW_SEGMENTS);
            float u0 = (float) i / GLOW_SEGMENTS;
            float u1 = (float) (i + 1) / GLOW_SEGMENTS;
            Vec3d b0 = ringPoint(center, u, v, a0, radius);
            Vec3d b1 = ringPoint(center, u, v, a1, radius);
            put(b0, u0, 0f);
            put(b1, u1, 0f);
            put(b1.add(normal), u1, 1f);
            put(b0, u0, 0f);
            put(b1.add(normal), u1, 1f);
            put(b0.add(normal), u0, 1f);
        }
        return GLOW_VERTICES;
    }

    private static Vec3d ringPoint(Vec3d center, Vec3d u, Vec3d v, float angle, float radius) {
        return center.add(v.multiply(Math.cos(angle) * radius)).add(u.multiply(Math.sin(angle) * radius));
    }

    private static void beginGeometry() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        CAMERA_POSITION.set(eye.x, eye.y, eye.z);
        WORLD_TO_VIEW.set(camera.getRotation()).conjugate();
        vertexData.clear();
    }

    private static void put(Vec3d world, float u, float v) {
        VIEW_POINT.set((float) (world.x - CAMERA_POSITION.x), (float) (world.y - CAMERA_POSITION.y),
                (float) (world.z - CAMERA_POSITION.z)).rotate(WORLD_TO_VIEW);
        vertexData.putFloat(VIEW_POINT.x).putFloat(VIEW_POINT.y).putFloat(VIEW_POINT.z)
                .putFloat(u).putFloat(v);
    }

    private static void upload(float p1, float p2, float p3, float p4, Color color) {
        vertexData.flip();
        encoder.writeToBuffer(vertexBuffer.slice(), vertexData);

        uniformData.clear();
        writeMatrix(uniformData, storedProjection);
        uniformData.putFloat(p1).putFloat(p2).putFloat(p3).putFloat(p4);
        uniformData.putFloat(color.getRed() / 255f).putFloat(color.getGreen() / 255f)
                .putFloat(color.getBlue() / 255f).putFloat(color.getAlpha() / 255f);
        uniformData.flip();
        encoder.writeToBuffer(uniformBuffer.slice(), uniformData);
    }

    private static void writeMatrix(ByteBuffer buffer, Matrix4f m) {
        buffer.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
        buffer.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
        buffer.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
        buffer.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
    }

    private static void ensureSceneTexture(int width, int height) {
        if (sceneTexture != null && sceneWidth == width && sceneHeight == height) return;
        closeSceneTexture();
        sceneTexture = RenderSystem.getDevice().createTexture(
                () -> "minar:impact_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneView = RenderSystem.getDevice().createTextureView(sceneTexture);
        sceneWidth = width;
        sceneHeight = height;
    }

    private static void ensureBuffers() {
        if (vertexData == null) {
            vertexData = MemoryUtil.memAlloc(VERTEX_CAPACITY);
        }
        if (uniformData == null) {
            uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        }
        if (vertexBuffer == null) {
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:impact_vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, VERTEX_CAPACITY);
        }
        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minar:impact_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
        }
    }

    private static void closeSceneTexture() {
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (sceneTexture != null) {
            sceneTexture.close();
            sceneTexture = null;
        }
        sceneWidth = 0;
        sceneHeight = 0;
    }

    public static void shutdown() {
        closeSceneTexture();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (vertexData != null) {
            MemoryUtil.memFree(vertexData);
            vertexData = null;
        }
        if (uniformData != null) {
            MemoryUtil.memFree(uniformData);
            uniformData = null;
        }
        encoder = null;
        framebuffer = null;
        storedProjection = null;
    }
}
