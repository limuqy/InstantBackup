package io.github.limuqy.mc.backup.i18n;

import io.github.limuqy.mc.backup.config.BackupConfig;
import io.github.limuqy.mc.backup.logging.ModLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无 Minecraft 依赖的国际化工具（Mod 与 CLI 共用）。
 */
public final class I18n {
    private static final String FALLBACK_LANGUAGE = "zh_cn";
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );

    private static Map<String, String> translations = Collections.emptyMap();
    private static String loadedLanguage = FALLBACK_LANGUAGE;

    private I18n() {
    }

    public static void reload() {
        String requested = normalizeLanguage(BackupConfig.getLanguage());
        if (requested == null) {
            requested = FALLBACK_LANGUAGE;
        }
        Map<String, String> loaded = loadLanguageFile(requested);
        if (loaded.isEmpty() && !FALLBACK_LANGUAGE.equals(requested)) {
            ModLog.warn("语言文件 {} 不可用，回退到 {}", requested, FALLBACK_LANGUAGE);
            loaded = loadLanguageFile(FALLBACK_LANGUAGE);
            requested = FALLBACK_LANGUAGE;
        }
        translations = loaded.isEmpty() ? Collections.emptyMap() : loaded;
        loadedLanguage = requested;
    }

    public static String getLoadedLanguage() {
        return loadedLanguage;
    }

    public static String normalizeLanguage(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        switch (normalized) {
            case "zh_cn":
            case "zh":
            case "chinese":
                return "zh_cn";
            case "en_us":
            case "en":
            case "english":
                return "en_us";
            default:
                return null;
        }
    }

    public static String format(String key, Object... args) {
        String template = translations.getOrDefault(key, key);
        if (args.length == 0) {
            return template;
        }
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception e) {
            ModLog.warn("翻译格式化失败: key={}, error={}", key, e.getMessage());
            return template;
        }
    }

    private static Map<String, String> loadLanguageFile(String language) {
        String resourcePath = "/assets/instantbackup/lang/" + language + ".json";
        try (InputStream inputStream = I18n.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                ModLog.warn("找不到语言文件: {}", resourcePath);
                return Collections.emptyMap();
            }
            String content = readStream(inputStream);
            return parseFlatJson(content);
        } catch (IOException e) {
            ModLog.warn("读取语言文件 {} 失败: {}", resourcePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            map.put(unescapeJson(matcher.group(1)), unescapeJson(matcher.group(2)));
        }
        return map;
    }

    private static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(next);
                        i++;
                        break;
                    case 'n':
                        result.append('\n');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'u':
                        if (i + 5 < value.length()) {
                            String hex = value.substring(i + 2, i + 6);
                            result.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        } else {
                            result.append(current);
                        }
                        break;
                    default:
                        result.append(current);
                        break;
                }
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }
}
