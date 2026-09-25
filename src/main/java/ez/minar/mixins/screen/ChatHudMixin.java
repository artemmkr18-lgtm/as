package ez.minar.mixins.screen;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ez.minar.Minar;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.features.misc.ChatHelper;
import ez.minar.system.menu.ThemeManager;
import ez.minar.utils.math.Easings;
import ez.minar.utils.render.chat.ChatAnimationRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;
import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;

    @Shadow public abstract void refresh();
    @Shadow public abstract int getLineHeight();
    @Shadow public abstract double getChatScale();

    @Unique private static boolean minar$animate = true;
    @Unique private static float minar$tick;
    @Unique private static boolean minar$focused;
    @Unique private static boolean minar$wasFocused;
    @Unique private static long minar$openStart = Long.MIN_VALUE;
    @Unique private static long minar$closeStart = Long.MIN_VALUE;
    @Unique private static int minar$focusedLines;
    @Unique private static float minar$lineOpacity = 1.0f;
    @Unique private static float minar$lineAlpha = 1.0f;
    @Unique private static float minar$lineShift = 0.0f;
    @Unique private static int minar$linesBefore;
    @Unique private static float minar$slideLines;
    @Unique private static long minar$slideStart = Long.MIN_VALUE;
    @Unique private static boolean minar$shifted;

    private String minar$lastText = "";
    private int minar$spamCount = 1;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true)
    private Text minar$modifyMessage(Text text) {
        ChatHelper helper = FunctionManager.getFunction(ChatHelper.class);
        if (helper != null && helper.isEnabled()) {
            String rawText = text.getString();

            if (helper.removeSpam.isEnabled()) {
                if (rawText.equals(minar$lastText)) {
                    minar$spamCount++;

                    MutableText spamText = Text.empty();
                    String suffix = " [x" + minar$spamCount + "]";
                    Color start = ThemeManager.getThemeColor();
                    Color end = Color.WHITE;

                    for (int i = 0; i < suffix.length(); i++) {
                        float progress = suffix.length() == 1 ? 1f : (float) i / (suffix.length() - 1);
                        int red = Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress);
                        int green = Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
                        int blue = Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
                        spamText.append(Text.literal(String.valueOf(suffix.charAt(i)))
                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((red << 16) | (green << 8) | blue))));
                    }

                    text = text.copy().append(spamText);

                    if (!messages.isEmpty()) {
                        messages.remove(0);
                        refresh();
                    }
                } else {
                    minar$lastText = rawText;
                    minar$spamCount = 1;
                }
            }
        }
        return text;
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void minar$rememberLineCount(Text text, MessageSignatureData messageSignatureData, MessageIndicator messageIndicator, CallbackInfo ci) {
        minar$linesBefore = this.visibleMessages != null ? this.visibleMessages.size() : 0;
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("TAIL"))
    private void minar$startSlide(Text text, MessageSignatureData messageSignatureData, MessageIndicator messageIndicator, CallbackInfo ci) {
        if (!minar$animate || this.scrolledLines > 0 || this.visibleMessages == null) {
            return;
        }
        int diff = this.visibleMessages.size() - minar$linesBefore;
        if (diff <= 0) {
            return;
        }
        minar$slideLines = Math.min(4, diff);
        minar$slideStart = System.currentTimeMillis();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
    private void minar$beginRender(DrawContext drawContext, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean isChatScreen, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        minar$tick = (float) currentTick + client.getRenderTickCounter().getTickProgress(false);
        minar$focused = focused;
        minar$lineOpacity = 1.0f;
        minar$lineAlpha = 1.0f;
        minar$lineShift = 0.0f;
        ChatAnimationRenderState.reset();
        minar$shifted = false;

        if (focused != minar$wasFocused) {
            minar$wasFocused = focused;
            if (focused) {
                minar$openStart = System.currentTimeMillis();
            } else {
                minar$closeStart = System.currentTimeMillis();
            }
        }

        if (!minar$animate || minar$slideStart == Long.MIN_VALUE) {
            return;
        }
        float progress = (float) (System.currentTimeMillis() - minar$slideStart) / 200.0f;
        if (progress >= 1.0f || progress < 0.0f) {
            return;
        }
        float ease = 1.0f - Easings.OutCubic(progress);
        float offset = ease * minar$slideLines * this.getLineHeight() * (float) this.getChatScale();
        if (offset <= 0.05f) {
            return;
        }

        drawContext.enableScissor(0, 0, drawContext.getScaledWindowWidth(), drawContext.getScaledWindowHeight() - 40);
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(0.0f, offset);
        minar$shifted = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("TAIL"))
    private void minar$endRender(DrawContext drawContext, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY, boolean focused, boolean isChatScreen, CallbackInfo ci) {
        if (minar$shifted) {
            minar$shifted = false;
            drawContext.getMatrices().popMatrix();
            drawContext.disableScissor();
        }
        this.minar$drawClosingInput(drawContext);
    }

    @Redirect(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
    private Object minar$captureLine(List<ChatHudLine.Visible> list, int index) {
        ChatHudLine.Visible visible = list.get(index);
        minar$lineOpacity = 1.0f;
        minar$lineAlpha = 1.0f;
        minar$lineShift = 0.0f;
        if (!minar$animate || visible == null) {
            return visible;
        }

        float life = minar$life(minar$tick - (float) visible.addedTime());
        float f;
        if (minar$focused) {
            minar$lineAlpha = f = MathHelper.lerp(minar$openProgress(), life, 1.0f);
        } else {
            minar$lineOpacity = f = Math.max(life, minar$closeFade());
        }
        minar$lineShift = (1.0f - f) * 12.0f;
        ChatAnimationRenderState.set(minar$lineAlpha, minar$lineShift);
        return visible;
    }

    @ModifyExpressionValue(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud$OpacityRule;calculate(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;)F"))
    private float minar$messageOpacity(float original) {
        return minar$animate ? minar$lineOpacity : original;
    }

    @Redirect(method = "forEachVisibleLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;getVisibleLineCount()I"))
    private int minar$visibleLineCount(ChatHud chatHud) {
        int count = chatHud.getVisibleLineCount();
        if (minar$focused) {
            minar$focusedLines = count;
            return count;
        }
        if (!minar$animate || minar$closeFade() <= 0.0f) {
            return count;
        }
        return Math.max(count, minar$focusedLines);
    }

    @Inject(method = "forEachVisibleLine", at = @At("RETURN"))
    private void minar$resetLineAnimation(CallbackInfoReturnable<Integer> cir) {
        minar$lineOpacity = 1.0f;
        minar$lineAlpha = 1.0f;
        minar$lineShift = 0.0f;
        ChatAnimationRenderState.reset();
    }

    @Unique
    private void minar$drawClosingInput(DrawContext drawContext) {
        if (!minar$animate || minar$focused) {
            return;
        }
        float fade = minar$closeFade();
        if (fade <= 0.0f) {
            return;
        }
        int w = drawContext.getScaledWindowWidth();
        int h = drawContext.getScaledWindowHeight();
        int bg = MinecraftClient.getInstance().options.getTextBackgroundColor(Integer.MIN_VALUE);
        int a = MathHelper.clamp((int) ((float) (bg >>> 24) * fade), 0, 255);
        Matrix3x2fStack matrixStack = drawContext.getMatrices();
        matrixStack.pushMatrix();
        matrixStack.translate(0.0f, (1.0f - fade) * 16.0f);
        drawContext.fill(2, h - 14, w - 2, h - 2, bg & 0xFFFFFF | a << 24);
        matrixStack.popMatrix();
    }

    @Unique
    private static float minar$life(float diff) {
        float f2 = MathHelper.clamp((200.0f - diff) / 4.0f, 0.0f, 1.0f);
        return f2 * f2;
    }

    @Unique
    private static float minar$openProgress() {
        if (minar$openStart == Long.MIN_VALUE) {
            return 1.0f;
        }
        float f = (float) (System.currentTimeMillis() - minar$openStart) / 200.0f;
        if (f <= 0.0f) return 0.0f;
        if (f >= 1.0f) return 1.0f;
        return Easings.OutCubic(f);
    }

    @Unique
    private static float minar$closeFade() {
        if (minar$closeStart == Long.MIN_VALUE) {
            return 0.0f;
        }
        float f = (float) (System.currentTimeMillis() - minar$closeStart) / 200.0f;
        if (f <= 0.0f) return 1.0f;
        if (f >= 1.0f) return 0.0f;
        return 1.0f - Easings.OutCubic(f);
    }
}
