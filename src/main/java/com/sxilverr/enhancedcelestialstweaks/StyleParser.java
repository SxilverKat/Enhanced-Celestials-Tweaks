package com.sxilverr.enhancedcelestialstweaks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

public final class StyleParser {

    private StyleParser() {}

    public static Style parse(String input, StringBuilder cleanText) {
        Style style = Style.EMPTY;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    style = style.applyFormat(fmt);
                    i += 2;
                    continue;
                }
            }
            cleanText.append(c);
            i++;
        }
        return style;
    }
}
