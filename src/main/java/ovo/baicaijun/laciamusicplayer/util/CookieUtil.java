package ovo.baicaijun.laciamusicplayer.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cookie处理工具类
 */
public class CookieUtil {

    /**
     * 从完整cookie字符串中提取关键cookie
     */
    public static String extractEssentialCookies(String cookieString) {
        if (cookieString == null || cookieString.isEmpty()) {
            return "";
        }

        // 定义需要提取的关键cookie名称
        String[] essentialCookieNames = {
                "MUSIC_U", "__csrf", "NMTID", "MUSIC_A_T",
                "MUSIC_R_T", "MUSIC_R_U"
        };

        Map<String, String> cookieMap = new LinkedHashMap<>();

        // 按分号分割cookie
        String[] cookieParts = cookieString.split(";");
        for (String part : cookieParts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // 查找等号位置
            int equalsIndex = part.indexOf('=');
            if (equalsIndex > 0) {
                String name = part.substring(0, equalsIndex).trim();
                String value = part.substring(equalsIndex + 1).trim();

                // 检查是否是关键cookie
                for (String essentialName : essentialCookieNames) {
                    if (name.equals(essentialName)) {
                        // 检查值是否包含额外的属性（如Expires、Path等）
                        if (!value.contains("Max-Age=") &&
                                !value.contains("Expires=") &&
                                !value.contains("Path=") &&
                                !value.contains("Domain=")) {
                            cookieMap.put(name, value);
                        }
                    }
                }
            }
        }

        // 构建cookie字符串
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return builder.toString();
    }

    /**
     * 验证cookie是否有效
     */
    public static boolean isValidCookie(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含必要的cookie
        boolean hasMusicU = cookie.contains("MUSIC_U=");
        boolean hasCsrf = cookie.contains("__csrf=");
        boolean hasNmtid = cookie.contains("NMTID=");

        // 至少需要MUSIC_U和__csrf
        return hasMusicU && hasCsrf;
    }

    /**
     * 清理重复的cookie条目
     */
    public static String deduplicateCookies(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return "";
        }

        Map<String, String> cookieMap = new LinkedHashMap<>();
        String[] parts = cookie.split(";");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int equalsIndex = part.indexOf('=');
            if (equalsIndex > 0) {
                String name = part.substring(0, equalsIndex).trim();
                String value = part.substring(equalsIndex + 1).trim();
                cookieMap.put(name, value);
            }
        }

        // 重新构建
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return builder.toString();
    }

    /**
     * 简单解析cookie字符串为名称-值对
     */
    public static Map<String, String> parseSimpleCookie(String cookie) {
        Map<String, String> result = new LinkedHashMap<>();
        if (cookie == null || cookie.isEmpty()) {
            return result;
        }

        String[] parts = cookie.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("=")) {
                String[] keyValue = part.split("=", 2);
                String key = keyValue[0].trim();
                String value = keyValue.length > 1 ? keyValue[1].trim() : "";
                result.put(key, value);
            }
        }

        return result;
    }
}