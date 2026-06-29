package io.github.limuqy.mc.backup.i18n;

import io.github.limuqy.mc.backup.compat.ChatCompat;
import net.minecraft.network.chat.Component;

/**
 * Mod 侧国际化门面，支持 {@link Component} 参数；底层委托 {@link I18n}。
 */
public final class ModI18n {
    private ModI18n() {
    }

    public static void reload() {
        I18n.reload();
    }

    public static String getLoadedLanguage() {
        return I18n.getLoadedLanguage();
    }

    public static String normalizeLanguage(String raw) {
        return I18n.normalizeLanguage(raw);
    }

    public static String format(String key, Object... args) {
        Object[] formattedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Component) {
                formattedArgs[i] = ChatCompat.getPlainText((Component) arg);
            } else {
                formattedArgs[i] = arg;
            }
        }
        return I18n.format(key, formattedArgs);
    }
}
