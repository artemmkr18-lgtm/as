package ez.minar.system.menu;

import java.awt.Color;

/**
 * Pixel-exact color palette transferred from the Figma design (Untitled / ClickGUI mock, 1920x1080).
 * Primary Accent: Soft Pink (#FFAFE2 / rgb(255, 175, 226))
 * Window Background: #181717 / rgb(24, 23, 23)
 * Bottom Bar Chips: #1D1C1C / rgb(29, 28, 28)
 * Surfaces: white @ 2% (panels, cards), white @ 4% (fields, badges, tracks)
 * Secondary text: white @ 25% (descriptions, icons), white @ 45% (field values)
 */
public final class GuiTheme {
    // Window & Backgrounds (Figma: window #181717, panels white@2%, chips #1D1C1C)
    public static final Color WINDOW_BG = new Color(24, 23, 23, 255);
    public static final Color OVERLAY_TINT = new Color(6, 4, 10, 160);
    public static final Color SIDEBAR_BG = new Color(255, 255, 255, 5);
    public static final Color MAIN_BG = new Color(24, 23, 23, 255);
    public static final Color PANEL_BG = new Color(24, 23, 23, 250);
    public static final Color PANEL_BLUR_TINT = new Color(24, 23, 23, 235);
    public static final Color SEARCH_BG = new Color(255, 255, 255, 10);
    public static final Color SEARCH_BLUR_TINT = new Color(255, 255, 255, 10);
    public static final Color CARD_BG = new Color(255, 255, 255, 5);
    public static final Color SETTINGS_BG = new Color(255, 255, 255, 5);
    public static final Color ACTION_ITEM_BG = new Color(255, 255, 255, 10);
    public static final Color TRACK_BG = new Color(255, 255, 255, 10);
    public static final Color SEGMENT_BG = new Color(255, 255, 255, 10);
    public static final Color BADGE_BG = new Color(255, 255, 255, 10);
    public static final Color BADGE_BORDER = new Color(255, 255, 255, 20);
    public static final Color DIVIDER = new Color(255, 255, 255, 15);

    // Accents (Figma: #FFAFE2, flat — no gradient)
    public static final Color ACCENT = new Color(255, 175, 226);
    public static final Color ACCENT_BRIGHT = new Color(255, 197, 235);
    public static final Color ACCENT_DARK = new Color(233, 146, 205);
    public static final Color ACCENT_GLOW = new Color(255, 175, 226, 90);
    public static final Color ACCENT_TINT = new Color(255, 175, 226, 36);

    // Module States
    public static final Color FIELD_BG = new Color(255, 255, 255, 5);
    public static final Color FIELD_HOVER = new Color(255, 255, 255, 10);
    public static final Color FIELD_ACTIVE = new Color(255, 255, 255, 10);
    public static final Color FIELD_ACTIVE_HOVER = new Color(255, 255, 255, 16);

    // Enabled module accent (flat pink, knob slides right)
    public static final Color FIELD_ON_TOP = new Color(255, 175, 226);
    public static final Color FIELD_ON_MID = new Color(255, 175, 226);
    public static final Color FIELD_ON_BOTTOM = new Color(255, 175, 226);
    public static final Color FIELD_ON_TOP_HOVER = new Color(255, 197, 235);
    public static final Color FIELD_ON_BOTTOM_HOVER = new Color(255, 163, 220);
    public static final Color FIELD_ON_GLOW = new Color(255, 175, 226, 110);
    public static final Color FIELD_BORDER_OFF = new Color(255, 255, 255, 20);
    public static final Color FIELD_BORDER_ON = new Color(255, 175, 226, 220);

    // Text Palette
    public static final Color TEXT_WHITE = new Color(255, 255, 255);
    public static final Color TEXT_TITLE = new Color(255, 255, 255);
    public static final Color TEXT_MUTED = new Color(255, 255, 255, 64);
    public static final Color TEXT_DIM = new Color(255, 255, 255, 115);
    public static final Color TEXT_ACCENT = new Color(255, 175, 226);
    public static final Color TEXT_SETTING_LABEL = new Color(255, 255, 255);
    public static final Color TEXT_SETTING_DIM = new Color(255, 255, 255, 115);
    public static final Color TEXT_VALUE = new Color(255, 175, 226);
    public static final Color TEXT_MORE_DOTS = new Color(255, 255, 255, 64);
    public static final Color TEXT_MORE_DOTS_ACTIVE = new Color(255, 175, 226);

    // Toggle Button Colors (Figma: off = white@4% pill + white knob left; on = #FFAFE2 pill + white knob right)
    public static final Color TOGGLE_OFF_BG = new Color(255, 255, 255, 10);
    public static final Color TOGGLE_OFF_BORDER = new Color(255, 255, 255, 0);
    public static final Color TOGGLE_OFF_CROSS = new Color(255, 255, 255, 115);
    public static final Color TOGGLE_ON_BG = new Color(255, 175, 226);
    public static final Color TOGGLE_ON_BORDER = new Color(255, 197, 235);
    public static final Color TOGGLE_ON_CHECK = new Color(255, 255, 255);

    // Chips / Modes (Figma: selected #FFAFE2 r6 + white text; idle white@4% + white@25% text)
    public static final Color CHIP_SELECTED_BG = new Color(255, 175, 226);
    public static final Color CHIP_SELECTED_TEXT = Color.WHITE;
    public static final Color CHIP_IDLE_BG = new Color(255, 255, 255, 10);
    public static final Color CHIP_IDLE_TEXT = new Color(255, 255, 255, 64);
    public static final Color CHIP_HOVER_BG = new Color(255, 255, 255, 20);
    public static final Color CHIP_HOVER_TEXT = new Color(255, 255, 255, 115);

    // Navigation Tab Colors
    public static final Color TAB_ACTIVE_BG = new Color(255, 175, 226, 36);
    public static final Color TAB_ACTIVE_BORDER = new Color(255, 175, 226, 200);
    public static final Color TAB_HOVER_BG = new Color(255, 255, 255, 10);

    private GuiTheme() {
    }
}
