package ez.minar.system.menu.components.settings;

import ez.minar.system.settings.Setting;
import net.minecraft.client.gui.DrawContext;

public interface SettingRenderer<T extends Setting> {
    boolean supports(Setting setting);

    void render(DrawContext context, T setting, SettingRendererContext rendererContext, float x, float y, float width, float scale, float opacity);

    boolean click(T setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width);

    default boolean drag(T setting, SettingRendererContext rendererContext, int button, float localX, float localY, float width) {
        return false;
    }

    float getHeight(T setting, SettingRendererContext rendererContext);

    String getValue(T setting);
}
