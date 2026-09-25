package ez.minar.mixins.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {
    @Shadow
    @Final
    private Map<UUID, ClientBossBar> bossBars;

    @Unique
    private static final Pattern PVP_TIME_PATTERN = Pattern.compile("(\\d+)\\s*(?:[сc][еe][кk]\\.?|[сc][еe][кk][уy]?[нnh][дd]?)(?=$|\\s|\\p{Punct})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    @Unique
    private static final Pattern PVP_TIME_LOOSE = Pattern.compile("(\\d{1,4})[^\\d]{0,4}?[сc][еe][кk]", Pattern.CASE_INSENSITIVE);
    @Unique
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d{1,4}");
    @Unique
    private static final String CYRILLIC = "асеорхукнвтм";
    @Unique
    private static final String LATIN = "aceopxykhbtm";

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(DrawContext drawContext, CallbackInfo callbackInfo) {
        int maxSec = 0;
        for (ClientBossBar bar : this.bossBars.values()) {
            if (bar.getName() == null) continue;
            String plain = minar$plain(bar.getName().getString());
            if (!minar$isPvpBar(plain)) continue;
            int sec = minar$seconds(plain);
            if (sec > maxSec) {
                maxSec = sec;
            }
        }
        ez.minar.utils.render.PvpTracker.update(maxSec > 0, maxSec);

        // While the island is on it owns the boss bar: name, colour and HP render inside the pill
        // (HUD#renderIsland), so the vanilla bar is cancelled rather than shifted out of the way.
        if (ez.minar.system.features.render.HUD.isIslandShowing() && !this.bossBars.isEmpty()) {
            callbackInfo.cancel();
        }
    }

    @Unique
    private static String minar$plain(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            if (c >= '\ue000' && c <= '\uf8ff' || Character.getType(c) == Character.CONTROL) continue;
            sb.append(Character.isSpaceChar(c) ? ' ' : c);
        }
        return sb.toString();
    }

    @Unique
    private static boolean minar$isPvpBar(String string) {
        String lower = string.toLowerCase(Locale.ROOT);
        if (lower.contains("бой") || lower.contains("бою") || lower.contains("пвп") || lower.contains("pvp")) {
            return true;
        }
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); ++i) {
            char c = lower.charAt(i);
            int idx = CYRILLIC.indexOf(c);
            sb.append(idx < 0 ? c : LATIN.charAt(idx));
        }
        return sb.indexOf("pvp") >= 0;
    }

    @Unique
    private static int minar$seconds(String string) {
        Matcher matcher = PVP_TIME_PATTERN.matcher(string);
        if (matcher.find()) {
            return minar$parse(matcher.group(1));
        }
        Matcher matcher2 = PVP_TIME_LOOSE.matcher(string);
        if (matcher2.find()) {
            return minar$parse(matcher2.group(1));
        }
        Matcher matcher3 = NUMBER_PATTERN.matcher(string);
        if (!matcher3.find()) {
            return -1;
        }
        String str = matcher3.group();
        return matcher3.find() ? -1 : minar$parse(str);
    }

    @Unique
    private static int minar$parse(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
