package ez.minar.system.menu.components.settings;

import ez.minar.system.settings.Setting;

import java.util.List;

public class SettingRendererRegistry {
    private final List<SettingRenderer<? extends Setting>> renderers = List.of(
            new ButtonSettingRenderer(),
            new BooleanSettingRenderer(),
            new ColorSettingRenderer(),
            new ModeSettingRenderer(),
            new NumberSettingRenderer(),
            new MultiSettingRenderer(),
            new TextSettingRenderer(),
            new KeybindSettingRenderer()
    );

    @SuppressWarnings("unchecked")
    public <T extends Setting> SettingRenderer<T> get(T setting) {
        for (SettingRenderer<? extends Setting> renderer : renderers) {
            if (renderer.supports(setting)) {
                return (SettingRenderer<T>) renderer;
            }
        }
        return (SettingRenderer<T>) FallbackSettingRenderer.INSTANCE;
    }

    private static class FallbackSettingRenderer implements SettingRenderer<Setting> {
        private static final FallbackSettingRenderer INSTANCE = new FallbackSettingRenderer();

        @Override
        public boolean supports(Setting setting) {
            return true;
        }

        @Override
        public void render(net.minecraft.client.gui.DrawContext context, Setting setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity) {
            rendererContext.renderRow(context, setting, getValue(setting), x, y, width, scale, opacity);
        }

        @Override
        public boolean click(Setting setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
            return true;
        }

        @Override
        public float getHeight(Setting setting, SettingRendererContext rendererContext) {
            return SettingRendererContext.SETTING_HEIGHT;
        }

        @Override
        public String getValue(Setting setting) {
            return "...";
        }
    }
}
