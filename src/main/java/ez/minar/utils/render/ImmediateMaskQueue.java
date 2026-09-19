package ez.minar.utils.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.List;

public record ImmediateMaskQueue(VertexConsumerProvider.Immediate consumers) implements OrderedRenderCommandQueue, RenderCommandQueue {
    @Override
    public RenderCommandQueue getBatchingQueue(int layer) {
        return this;
    }

    @Override
    public <T> void submitModel(Model<? super T> model, T state, MatrixStack matrices, RenderLayer layer, int light,
                                int overlay, int color, Sprite sprite, int outlineColor,
                                ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        model.setAngles(state);
        model.render(matrices, consumers.getBuffer(layer), light, overlay, color);
    }

    @Override
    public void submitModelPart(ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                Sprite sprite, boolean applyTransform, boolean renderCuboids, int color,
                                ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, int outlineColor) {
        part.render(matrices, consumers.getBuffer(layer), light, overlay, color);
    }

    @Override
    public void submitShadowPieces(MatrixStack matrices, float opacity, List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitLabel(MatrixStack matrices, Vec3d pos, int yOffset, Text text, boolean seeThrough, int light,
                            double distance, CameraRenderState cameraRenderState) {
    }

    @Override
    public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean shadow,
                           TextRenderer.TextLayerType layerType, int color, int backgroundColor, int light, int seed) {
    }

    @Override
    public void submitFire(MatrixStack matrices, EntityRenderState state, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) {
    }

    @Override
    public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int color) {
    }

    @Override
    public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
    }

    @Override
    public void submitBlockStateModel(MatrixStack matrices, RenderLayer layer, BlockStateModel model, float red,
                                      float green, float blue, int light, int overlay, int color) {
    }

    @Override
    public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay, int seed,
                           int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint) {
    }

    @Override
    public void submitCustom(MatrixStack matrices, RenderLayer layer, OrderedRenderCommandQueue.Custom command) {
    }

    @Override
    public void submitCustom(OrderedRenderCommandQueue.LayeredCustom command) {
    }
}
