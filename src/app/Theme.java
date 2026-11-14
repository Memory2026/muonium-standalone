package app;

import java.awt.Color;

/**
 * 从 MuoniumPlayer 原生 LIQUID_GLASS 主题字节码中拆出的配色（RGB，无 alpha）。
 * 构造顺序：genericBackground, elementBackground, elementHover, primaryText,
 * secondaryText, accent, accentHover, inputBackground, navigationBackground, border。
 */
public final class Theme {
    private Theme() {}

    public static final Color GENERIC_BG   = new Color(0x18, 0x23, 0x31);
    public static final Color ELEMENT_BG   = new Color(0x26, 0x38, 0x4B);
    public static final Color ELEMENT_HOVER= new Color(0x49, 0x63, 0x7C);
    public static final Color PRIMARY_TEXT = new Color(0xF7, 0xFB, 0xFF);
    public static final Color SECONDARY_TEXT = new Color(0xB7, 0xC6, 0xD8);
    public static final Color ACCENT       = new Color(0x64, 0xA8, 0xFF);
    public static final Color ACCENT_HOVER = new Color(0x8B, 0xC1, 0xFF);
    public static final Color INPUT_BG     = new Color(0x20, 0x30, 0x44);
    public static final Color NAV_BG       = new Color(0x11, 0x1C, 0x2A);
    public static final Color BORDER       = new Color(0x91, 0xA8, 0xC2);

    /** 半透明版本，用于玻璃面板叠层。 */
    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
