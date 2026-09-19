package ez.minar.mixins.screen;

import ez.minar.system.managers.UnhookManager;
import ez.minar.system.menu.TitleScreenMenuRenderer;
import ez.minar.utils.render.RenderUtil;
import ez.minar.utils.render.pipeline.TitleBackgroundPipeline;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/TitleScreen;renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V"))
    private void minar$renderCustomBackground(TitleScreen screen, DrawContext context, float deltaTicks) {
        if (UnhookManager.isUnhooked()) {
            renderPanoramaBackground(context, deltaTicks);
            return;
        }

        TitleScreenMenuRenderer.renderBackground();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void minar$captureContext(DrawContext context, int mouseX, int mouseY, float deltaTicks,
                                        CallbackInfo ci) {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        TitleScreenMenuRenderer.hideTitleScreenExtras((TitleScreen) (Object) this);
        TitleScreenMenuRenderer.setContext(context);
        TitleScreenMenuRenderer.updateMouse(mouseX, mouseY);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void minar$layoutButtons(CallbackInfo ci) {
        if (UnhookManager.isUnhooked()) {
            return;
        }

        TitleScreen screen = (TitleScreen) (Object) this;
        List<ButtonWidget.Text> buttons = new ArrayList<>();

        TitleScreenMenuRenderer.hideTitleScreenExtras(screen);

        for (Element element : screen.children()) {
            if (element instanceof TextIconButtonWidget iconButton) {
                iconButton.visible = false;
                iconButton.active = false;
            } else if (element instanceof PressableTextWidget textWidget) {
                textWidget.visible = false;
                textWidget.active = false;
            } else if (element instanceof ButtonWidget.Text textButton) {
                buttons.add(textButton);
            }
        }

        float scaleFactor = RenderUtil.getScaleFactor();
        float centerX = RenderUtil.getFixedScaledWidth() / 2f;
        int size = 42;
        int gap = 13;
        float y = RenderUtil.getFixedScaledHeight() / 4f + 118f;

        ButtonWidget altManagerBtn = ButtonWidget.builder(net.minecraft.text.Text.literal("Alt"), btn -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new ez.minar.system.ui.alts.AltManagerScreen(screen));
        }).dimensions(0, 0, size, size).build();

        ButtonWidget langBtn = ButtonWidget.builder(net.minecraft.text.Text.literal("Language"), btn -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new ez.minar.system.menu.language.LanguageSelectScreen(screen));
        }).dimensions(0, 0, size, size).build();
        
        this.addDrawableChild(altManagerBtn);
        this.addDrawableChild(langBtn);
        
        List<ButtonWidget> layoutButtons = new ArrayList<>();
        if (buttons.size() >= 5) {
            layoutButtons.add(buttons.get(0)); // singleplayer
            layoutButtons.add(buttons.get(1)); // multiplayer
            layoutButtons.add(altManagerBtn);  // alt manager
            layoutButtons.add(langBtn);        // language selector
            layoutButtons.add(buttons.get(3)); // options
            layoutButtons.add(buttons.get(4)); // quit
            
            buttons.get(2).visible = false; // hide realms
            buttons.get(2).active = false;
        } else {
            layoutButtons.add(altManagerBtn);
            layoutButtons.add(langBtn);
        }

        int rowWidth = size * layoutButtons.size() + gap * (layoutButtons.size() - 1);

        for (int i = 0; i < layoutButtons.size(); i++) {
            float fixedX = centerX - rowWidth / 2f + (size + gap) * i;
            layoutButtons.get(i).setDimensionsAndPosition(
                    Math.round(size / scaleFactor), Math.round(size / scaleFactor),
                    Math.round(fixedX / scaleFactor), Math.round(y / scaleFactor));
        }

        if (!ez.minar.system.managers.LanguageSelectManager.hasSelectedLanguage()
                && !ez.minar.system.managers.LanguageSelectManager.hasPromptedThisSession()) {
            ez.minar.system.managers.LanguageSelectManager.setPromptedThisSession(true);
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                    new ez.minar.system.menu.language.LanguageSelectScreen(screen)
            );
        }
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
    private void minar$hideMinecraftLogo(LogoDrawer logoDrawer, DrawContext context, int screenWidth, float alpha) {
        if (UnhookManager.isUnhooked()) {
            logoDrawer.draw(context, screenWidth, alpha);
        }
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;F)V"))
    private void minar$hideSplashText(SplashTextRenderer splashText, DrawContext context, int screenWidth,
                                        TextRenderer textRenderer, float alpha) {
        if (UnhookManager.isUnhooked()) {
            splashText.render(context, screenWidth, textRenderer, alpha);
        }
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
    private void minar$hideVersionText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        if (UnhookManager.isUnhooked()) {
            context.drawTextWithShadow(textRenderer, text, x, y, color);
        }
    }
}
