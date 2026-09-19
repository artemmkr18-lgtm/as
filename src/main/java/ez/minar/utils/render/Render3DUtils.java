package ez.minar.utils.render;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;

public class Render3DUtils {
    private static final RenderPipeline TEXTURE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "textured_billboard"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build()
    );
    private static final Function<Identifier, RenderLayer> TEXTURE_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_textured_billboard",
                    RenderSetup.builder(TEXTURE_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));
    private static final RenderPipeline ADDITIVE_TEXTURE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "additive_textured_billboard"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build()
    );
    public static final Function<Identifier, RenderLayer> ADDITIVE_TEXTURE_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_additive_textured_billboard",
                    RenderSetup.builder(ADDITIVE_TEXTURE_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));
    private static final RenderPipeline ADDITIVE_TEXTURE_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "additive_textured_billboard_no_depth"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    public static final Function<Identifier, RenderLayer> ADDITIVE_TEXTURE_NO_DEPTH_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_additive_textured_billboard_no_depth",
                    RenderSetup.builder(ADDITIVE_TEXTURE_NO_DEPTH_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));
    private static final RenderPipeline SHADER_GLOW_TEXTURE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "shader_glow_textured_billboard"))
                    .withVertexShader(Identifier.of("minar", "billboard_glow_vertex"))
                    .withFragmentShader(Identifier.of("minar", "billboard_glow_fragment"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build()
    );
    private static final Function<Identifier, RenderLayer> SHADER_GLOW_TEXTURE_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_shader_glow_textured_billboard",
                    RenderSetup.builder(SHADER_GLOW_TEXTURE_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));
    private static final RenderPipeline SHADER_GLOW_TEXTURE_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "shader_glow_textured_billboard_no_depth"))
                    .withVertexShader(Identifier.of("minar", "billboard_glow_vertex"))
                    .withFragmentShader(Identifier.of("minar", "billboard_glow_fragment"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    private static final Function<Identifier, RenderLayer> SHADER_GLOW_TEXTURE_NO_DEPTH_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_shader_glow_textured_billboard_no_depth",
                    RenderSetup.builder(SHADER_GLOW_TEXTURE_NO_DEPTH_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));
    private static final RenderPipeline TEXTURE_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.of("minar", "textured_billboard_no_depth"))
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    private static final Function<Identifier, RenderLayer> TEXTURE_NO_DEPTH_LAYER = Util.memoize(texture ->
            RenderLayer.of("minar_textured_billboard_no_depth",
                    RenderSetup.builder(TEXTURE_NO_DEPTH_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .expectedBufferSize(1536)
                            .build()));

    private Render3DUtils() {
    }

    public static TexturedBillboardBatch texturedBillboardBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, TEXTURE_LAYER.apply(texture));
    }

    public static RenderLayer texturedLayer(Identifier texture) {
        return TEXTURE_LAYER.apply(texture);
    }

    public static TexturedBillboardBatch additiveTexturedBillboardBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, ADDITIVE_TEXTURE_LAYER.apply(texture));
    }

    public static TexturedBillboardBatch additiveTexturedBillboardNoDepthBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(texture));
    }

    public static TexturedBillboardBatch shaderGlowTexturedBillboardBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, SHADER_GLOW_TEXTURE_LAYER.apply(texture));
    }

    public static TexturedBillboardBatch shaderGlowTexturedBillboardNoDepthBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, SHADER_GLOW_TEXTURE_NO_DEPTH_LAYER.apply(texture));
    }

    public static TexturedBillboardBatch texturedBillboardNoDepthBatch(WorldRenderContext context, Identifier texture) {
        return texturedBillboardBatch(context, TEXTURE_NO_DEPTH_LAYER.apply(texture));
    }

    public static TexturedSurfaceBatch additiveTexturedSurfaceNoDepthBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(texture));

        return new TexturedSurfaceBatch(buffer, camera);
    }

    public static TexturedSurfaceBatch shaderGlowTexturedSurfaceNoDepthBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(SHADER_GLOW_TEXTURE_NO_DEPTH_LAYER.apply(texture));

        return new TexturedSurfaceBatch(buffer, camera);
    }

    public static TexturedBillboardBatch texturedBillboardBatch(WorldRenderContext context, RenderLayer layer) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        Quaternionf orientation = context.worldState().cameraRenderState.orientation;
        Vector3f cameraRight = orientation.transform(new Vector3f(1f, 0f, 0f));
        Vector3f cameraUp = orientation.transform(new Vector3f(0f, 1f, 0f));
        VertexConsumer buffer = context.consumers().getBuffer(layer);

        return new TexturedBillboardBatch(buffer, camera, cameraRight, cameraUp);
    }

    public static TexturedPlaneBatch texturedPlaneBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(TEXTURE_LAYER.apply(texture));

        return new TexturedPlaneBatch(buffer, camera);
    }

    public static TexturedPlaneBatch additiveTexturedPlaneBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(ADDITIVE_TEXTURE_LAYER.apply(texture));

        return new TexturedPlaneBatch(buffer, camera);
    }

    public static TexturedPlaneBatch shaderGlowTexturedPlaneBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(SHADER_GLOW_TEXTURE_LAYER.apply(texture));

        return new TexturedPlaneBatch(buffer, camera);
    }

    public static void texturedBillboard(WorldRenderContext context, Identifier texture, Vec3d position, float size,
                                         float rotationDegrees, float alpha) {
        texturedBillboardBatch(context, texture).render(position, size, rotationDegrees, alpha);
    }

    public static void additiveTexturedBillboard(WorldRenderContext context, Identifier texture, Vec3d position,
                                                 float size, float rotationDegrees, float alpha) {
        additiveTexturedBillboardBatch(context, texture).render(position, size, rotationDegrees, alpha);
    }

    public static void additiveTexturedBillboardGlow(WorldRenderContext context, Identifier texture, Vec3d position,
                                                     float size, float rotationDegrees, float alpha,
                                                     float glowScale, float glowAlpha) {
        additiveTexturedBillboardBatch(context, texture)
                .renderGlow(position, size, rotationDegrees, alpha, glowScale, glowAlpha);
    }

    public static void additiveTexturedBillboardGlow(WorldRenderContext context, Identifier texture, Identifier glowTexture,
                                                     Vec3d position, float size, float rotationDegrees, float alpha,
                                                     float glowScale, float glowAlpha) {
        additiveTexturedBillboardBatch(context, glowTexture)
                .render(position, size * glowScale, rotationDegrees, alpha * glowAlpha);
        additiveTexturedBillboardBatch(context, texture)
                .render(position, size, rotationDegrees, alpha);
    }



    public static TexturedCubeBatch additiveTexturedCubeNoDepthBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;
        VertexConsumer buffer = context.consumers().getBuffer(ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(texture));
        return new TexturedCubeBatch(buffer, camera);
    }

    public static class TexturedCubeBatch {
        private final VertexConsumer buffer;
        private final Vec3d camera;

        private TexturedCubeBatch(VertexConsumer buffer, Vec3d camera) {
            this.buffer = buffer;
            this.camera = camera;
        }

        public void render(Vec3d position, float size, float rotX, float rotY, float rotZ,
                           int red, int green, int blue, float alpha) {
            if (alpha <= 0f || size <= 0f) return;

            float half = size * 0.5f;
            Quaternionf rotation = new Quaternionf()
                    .rotateX(rotX * ((float) Math.PI / 180f))
                    .rotateY(rotY * ((float) Math.PI / 180f))
                    .rotateZ(rotZ * ((float) Math.PI / 180f));

            Vector3f[] v = new Vector3f[] {
                    vertex(-half, -half, -half, rotation, position),
                    vertex( half, -half, -half, rotation, position),
                    vertex( half,  half, -half, rotation, position),
                    vertex(-half,  half, -half, rotation, position),
                    vertex(-half, -half,  half, rotation, position),
                    vertex( half, -half,  half, rotation, position),
                    vertex( half,  half,  half, rotation, position),
                    vertex(-half,  half,  half, rotation, position)
            };

            face(v[0], v[1], v[2], v[3], red, green, blue, alpha); // back
            face(v[5], v[4], v[7], v[6], red, green, blue, alpha); // front
            face(v[4], v[0], v[3], v[7], red, green, blue, alpha); // left
            face(v[1], v[5], v[6], v[2], red, green, blue, alpha); // right
            face(v[3], v[2], v[6], v[7], red, green, blue, alpha); // top
            face(v[4], v[5], v[1], v[0], red, green, blue, alpha); // bottom
        }

        private Vector3f vertex(float x, float y, float z, Quaternionf rotation, Vec3d position) {
            Vector3f out = new Vector3f(x, y, z).rotate(rotation);
            out.add((float) (position.x - camera.x), (float) (position.y - camera.y), (float) (position.z - camera.z));
            return out;
        }

        private void face(Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                          int red, int green, int blue, float alpha) {
            vertex(a, 0f, 1f, red, green, blue, alpha);
            vertex(b, 1f, 1f, red, green, blue, alpha);
            vertex(c, 1f, 0f, red, green, blue, alpha);
            vertex(d, 0f, 0f, red, green, blue, alpha);
        }

        private void vertex(Vector3f pos, float u, float v, int red, int green, int blue, float alpha) {
            buffer.vertex(pos.x, pos.y, pos.z)
                    .color(red, green, blue, (int) (Math.clamp(alpha, 0f, 1f) * 255f))
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(0f, 1f, 0f);
        }
    }

    public static class TexturedBillboardBatch {
        private final VertexConsumer buffer;
        private final Vec3d camera;
        private final Vector3f cameraRight;
        private final Vector3f cameraUp;

        private TexturedBillboardBatch(VertexConsumer buffer, Vec3d camera, Vector3f cameraRight, Vector3f cameraUp) {
            this.buffer = buffer;
            this.camera = camera;
            this.cameraRight = cameraRight;
            this.cameraUp = cameraUp;
        }

        public void render(Vec3d position, float size, float rotationDegrees, float alpha) {
            render(position, size, rotationDegrees, 255, 255, 255, alpha);
        }

        public void render(Vec3d position, float size, float rotationDegrees, int red, int green, int blue, float alpha) {
            renderQuad(position, size, rotationDegrees, red, green, blue, alpha);
        }

        public void renderShaderGlow(Vec3d position, float size, float rotationDegrees, int red, int green, int blue, float alpha) {
            renderQuad(position, size * 1.45f, rotationDegrees, red, green, blue, alpha);
        }

        public void renderGlow(Vec3d position, float size, float rotationDegrees, float alpha,
                               float glowScale, float glowAlpha) {
            renderGlow(position, size, rotationDegrees, 255, 255, 255, alpha, glowScale, glowAlpha);
        }

        public void renderGlow(Vec3d position, float size, float rotationDegrees, int red, int green, int blue,
                               float alpha, float glowScale, float glowAlpha) {
            if (alpha <= 0f) return;

            renderQuad(position, size * glowScale, rotationDegrees, red, green, blue, alpha * glowAlpha);
            renderQuad(position, size, rotationDegrees, red, green, blue, alpha);
        }

        private void renderQuad(Vec3d position, float size, float rotationDegrees, int red, int green, int blue, float alpha) {
            float spin = rotationDegrees * ((float) Math.PI / 180f);
            Vector3f right = new Vector3f(cameraRight)
                    .mul((float) Math.cos(spin))
                    .add(new Vector3f(cameraUp).mul((float) Math.sin(spin)))
                    .mul(size);
            Vector3f up = new Vector3f(cameraUp)
                    .mul((float) Math.cos(spin))
                    .sub(new Vector3f(cameraRight).mul((float) Math.sin(spin)))
                    .mul(size);

            float x = (float) (position.x - camera.x);
            float y = (float) (position.y - camera.y);
            float z = (float) (position.z - camera.z);

            vertex(x - right.x - up.x, y - right.y - up.y, z - right.z - up.z, 0f, 1f, red, green, blue, alpha);
            vertex(x - right.x + up.x, y - right.y + up.y, z - right.z + up.z, 0f, 0f, red, green, blue, alpha);
            vertex(x + right.x + up.x, y + right.y + up.y, z + right.z + up.z, 1f, 0f, red, green, blue, alpha);
            vertex(x + right.x - up.x, y + right.y - up.y, z + right.z - up.z, 1f, 1f, red, green, blue, alpha);
        }

        private void vertex(float x, float y, float z, float u, float v, int red, int green, int blue, float alpha) {
            buffer.vertex(x, y, z)
                    .color(red, green, blue, (int) (Math.clamp(alpha, 0f, 1f) * 255f))
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(0f, 1f, 0f);
        }
    }

    public static class TexturedPlaneBatch {
        private final VertexConsumer buffer;
        private final Vec3d camera;

        private TexturedPlaneBatch(VertexConsumer buffer, Vec3d camera) {
            this.buffer = buffer;
            this.camera = camera;
        }

        public void render(Vec3d position, float size, float rotationDegrees, int red, int green, int blue, float alpha) {
            if (alpha <= 0f || size <= 0f) return;

            float half = size * 0.5f;
            float spin = rotationDegrees * ((float) Math.PI / 180f);
            float cos = (float) Math.cos(spin);
            float sin = (float) Math.sin(spin);

            vertex(position, -half, -half, cos, sin, 0f, 1f, red, green, blue, alpha);
            vertex(position, -half, half, cos, sin, 0f, 0f, red, green, blue, alpha);
            vertex(position, half, half, cos, sin, 1f, 0f, red, green, blue, alpha);
            vertex(position, half, -half, cos, sin, 1f, 1f, red, green, blue, alpha);
        }

        public void renderShaderGlow(Vec3d position, float size, float rotationDegrees, int red, int green, int blue, float alpha) {
            render(position, size * 1.45f, rotationDegrees, red, green, blue, alpha);
        }

        private void vertex(Vec3d position, float localX, float localZ, float cos, float sin,
                            float u, float v, int red, int green, int blue, float alpha) {
            float rotatedX = localX * cos - localZ * sin;
            float rotatedZ = localX * sin + localZ * cos;

            buffer.vertex(
                            (float) (position.x - camera.x) + rotatedX,
                            (float) (position.y - camera.y),
                            (float) (position.z - camera.z) + rotatedZ
                    )
                    .color(red, green, blue, (int) (Math.clamp(alpha, 0f, 1f) * 255f))
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(0f, 1f, 0f);
        }
    }

    public static class TexturedSurfaceBatch {
        private final VertexConsumer buffer;
        private final Vec3d camera;

        private TexturedSurfaceBatch(VertexConsumer buffer, Vec3d camera) {
            this.buffer = buffer;
            this.camera = camera;
        }

        public void render(Vec3d position, Vec3d normal, float size, float rotationDegrees,
                           int red, int green, int blue, float alpha) {
            if (alpha <= 0f || size <= 0f || normal.lengthSquared() <= 0.0001) return;

            Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
            Vector3f reference = Math.abs(n.y) < 0.99f ? new Vector3f(0f, 1f, 0f) : new Vector3f(1f, 0f, 0f);
            Vector3f right = reference.cross(n, new Vector3f()).normalize();
            Vector3f up = n.cross(right, new Vector3f()).normalize();

            float half = size * 0.5f;
            float spin = rotationDegrees * ((float) Math.PI / 180f);
            Vector3f rotatedRight = new Vector3f(right)
                    .mul((float) Math.cos(spin))
                    .add(new Vector3f(up).mul((float) Math.sin(spin)))
                    .mul(half);
            Vector3f rotatedUp = new Vector3f(up)
                    .mul((float) Math.cos(spin))
                    .sub(new Vector3f(right).mul((float) Math.sin(spin)))
                    .mul(half);

            float x = (float) (position.x - camera.x);
            float y = (float) (position.y - camera.y);
            float z = (float) (position.z - camera.z);

            vertex(x - rotatedRight.x - rotatedUp.x, y - rotatedRight.y - rotatedUp.y, z - rotatedRight.z - rotatedUp.z, n, 0f, 1f, red, green, blue, alpha);
            vertex(x - rotatedRight.x + rotatedUp.x, y - rotatedRight.y + rotatedUp.y, z - rotatedRight.z + rotatedUp.z, n, 0f, 0f, red, green, blue, alpha);
            vertex(x + rotatedRight.x + rotatedUp.x, y + rotatedRight.y + rotatedUp.y, z + rotatedRight.z + rotatedUp.z, n, 1f, 0f, red, green, blue, alpha);
            vertex(x + rotatedRight.x - rotatedUp.x, y + rotatedRight.y - rotatedUp.y, z + rotatedRight.z - rotatedUp.z, n, 1f, 1f, red, green, blue, alpha);
        }

        public void renderShaderGlow(Vec3d position, Vec3d normal, float size, float rotationDegrees,
                                     int red, int green, int blue, float alpha) {
            render(position, normal, size * 1.45f, rotationDegrees, red, green, blue, alpha);
        }

        private void vertex(float x, float y, float z, Vector3f normal, float u, float v,
                            int red, int green, int blue, float alpha) {
            buffer.vertex(x, y, z)
                    .color(red, green, blue, (int) (Math.clamp(alpha, 0f, 1f) * 255f))
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(normal.x, normal.y, normal.z);
        }
    }

    public static CylinderBatch cylinderBatch(WorldRenderContext context, Identifier texture) {
        Vec3d camera = context.worldState().cameraRenderState.pos;

        VertexConsumer buffer =
                context.consumers().getBuffer(
                        ADDITIVE_TEXTURE_NO_DEPTH_LAYER.apply(texture)
                );

        return new CylinderBatch(buffer, camera);
    }


    public static class CylinderBatch {
        private final VertexConsumer buffer;
        private final Vec3d camera;

        private CylinderBatch(VertexConsumer buffer, Vec3d camera) {
            this.buffer = buffer;
            this.camera = camera;
        }

        public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                         int red, int green, int blue, float alpha) {
            vertex(a, red, green, blue, alpha);
            vertex(b, red, green, blue, alpha);
            vertex(c, red, green, blue, alpha);
            vertex(d, red, green, blue, alpha);
        }

        private void vertex(Vec3d pos, int red, int green, int blue, float alpha) {
            buffer.vertex(
                    (float) (pos.x - camera.x),
                    (float) (pos.y - camera.y),
                    (float) (pos.z - camera.z)
            ).color(red, green, blue, (int) (Math.clamp(alpha, 0f, 1f) * 255f));
        }
        public void line(Vec3d from, Vec3d to,
                         int red, int green, int blue,
                         float alphaFrom, float alphaTo) {

            vertex(from, red, green, blue, alphaFrom);
            vertex(to, red, green, blue, alphaTo);
        }
    }

}
