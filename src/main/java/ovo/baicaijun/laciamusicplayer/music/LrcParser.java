package ovo.baicaijun.laciamusicplayer.music;

import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析器 - 增强双语支持
 * (最终修复版：解决了文件编码和双语歌词加载不全的问题，并保留完整歌词行)
 */
public class LrcParser {

    // 正则表达式，用于匹配 [mm:ss.xx] 或 [mm:ss.xxx] 格式的时间标签
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");
    // 正则表达式，用于匹配 [mm:ss] 格式的简单时间标签
    private static final Pattern SIMPLE_TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\]");

    public static TreeMap<Long, String> parseLrcString(String lrc) {
        TreeMap<Long, String> lyrics = new TreeMap<>();
        // [关键修复 1] 使用 BufferedReader 并明确指定 UTF-8 编码读取文件，
        // 防止因系统默认编码与文件编码不匹配导致的乱码或加载中断问题。
        String[] lines = lrc.split("\n");
        String line;
        int lineCount = 0;
        int lyricCount = 0;
        for (String s : lines) {
            line = s;
            lineCount++;
            int found = parseLineEnhanced(line, lyrics); // 解析每一行
            lyricCount += found;
        }

        return lyrics;
    }

    /**
     * 解析 LRC 文件。这是该类的主要入口方法。
     *
     * @param lrcFile 要解析的歌词文件
     * @return 一个 TreeMap，键是毫秒时间戳，值是对应的歌词文本
     */
    public static TreeMap<Long, String> parseLrcFile(File lrcFile) {
        TreeMap<Long, String> lyrics = new TreeMap<>();

        if (lrcFile == null || !lrcFile.exists() || !lrcFile.getName().toLowerCase().endsWith(".lrc")) {
            return lyrics; // 如果文件无效，返回空列表
        }

        // [关键修复 1] 使用 BufferedReader 并明确指定 UTF-8 编码读取文件，
        // 防止因系统默认编码与文件编码不匹配导致的乱码或加载中断问题。
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(lrcFile), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            int lyricCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                int found = parseLineEnhanced(line, lyrics); // 解析每一行
                lyricCount += found;
            }

            LaciamusicplayerClient.LOGGER.info("Parsed LRC file '{}': {} lines read, {} lyrics loaded.",
                    lrcFile.getName(), lineCount, lyricCount);

        } catch (IOException e) {
            LaciamusicplayerClient.LOGGER.error("Failed to read or parse LRC file: {}", lrcFile.getName(), e);
        }

        return lyrics;
    }

    /**
     * 增强的歌词行解析逻辑，作为内部处理函数。
     */
    private static int parseLineEnhanced(String line, TreeMap<Long, String> lyrics) {
        if (line == null || line.trim().isEmpty()) {
            return 0; // 跳过空行
        }

        // 移除 UTF-8 的 BOM 头（如果存在），并去除首尾空格
        line = line.replace("\uFEFF", "").trim();

        // 跳过元数据行 (如 [ti:歌曲名], [ar:歌手])
        if (isMetadataLine(line)) {
            return 0;
        }

        int lyricsFound = 0;

        // 判断并处理一行有多个时间标签的情况 (如 [00:01.00][00:05.00]歌词)
        if (line.contains("][") && line.contains("]")) {
            lyricsFound += parseMultipleTimeTags(line, lyrics);
        } else {
            // 处理常规的单时间标签行
            lyricsFound += parseSingleTimeTags(line, lyrics);
        }

        return lyricsFound;
    }

    /**
     * 解析包含多个时间标签的行。
     */
    private static int parseMultipleTimeTags(String line, TreeMap<Long, String> lyrics) {
        int lyricsFound = 0;

        // 提取歌词文本 (最后一个 ']' 之后的所有内容)
        int lastBracketIndex = line.lastIndexOf(']');
        if (lastBracketIndex == -1 || lastBracketIndex >= line.length() - 1) {
            return 0; // 格式错误，没有歌词文本
        }
        String lyricText = line.substring(lastBracketIndex + 1).trim();

        // [关键修复 2] 移除了对 isTranslationLine 的调用，只判断文本是否为空。
        // 这确保了即便是双语歌词（原文 | 译文）也能被完整加载。
        if (lyricText.isEmpty()) {
            return 0;
        }

        // 遍历所有时间标签并关联同一个歌词文本
        Matcher detailedMatcher = TIME_PATTERN.matcher(line);
        while (detailedMatcher.find()) {
            try {
                long timeMs = parseTimeToMs(detailedMatcher.group(1), detailedMatcher.group(2), detailedMatcher.group(3));
                lyrics.put(timeMs, lyricText);
                lyricsFound++;
            } catch (NumberFormatException e) {
                LaciamusicplayerClient.LOGGER.debug("Invalid time format in multi-tag line: {}", line);
            }
        }
        // 如果上面没匹配到，尝试简单格式
        if (lyricsFound == 0) {
            Matcher simpleMatcher = SIMPLE_TIME_PATTERN.matcher(line);
            while (simpleMatcher.find()) {
                try {
                    long timeMs = parseTimeToMs(simpleMatcher.group(1), simpleMatcher.group(2), "0");
                    lyrics.put(timeMs, lyricText);
                    lyricsFound++;
                } catch (NumberFormatException e) {
                    LaciamusicplayerClient.LOGGER.debug("Invalid simple time format in multi-tag line: {}", line);
                }
            }
        }

        return lyricsFound;
    }

    /**
     * 解析包含单个时间标签的行。
     */
    private static int parseSingleTimeTags(String line, TreeMap<Long, String> lyrics) {
        int lyricsFound = 0;

        // 优先尝试匹配详细时间格式 [mm:ss.xx]
        Matcher detailedMatcher = TIME_PATTERN.matcher(line);
        if (detailedMatcher.find()) {
            try {
                long timeMs = parseTimeToMs(detailedMatcher.group(1), detailedMatcher.group(2), detailedMatcher.group(3));
                String lyricText = extractCleanLyricText(line, detailedMatcher.end());

                // [关键修复 2] 同样移除了 isTranslationLine 的判断。
                if (!lyricText.isEmpty()) {
                    lyrics.put(timeMs, lyricText);
                    lyricsFound++;
                }
            } catch (NumberFormatException e) {
                LaciamusicplayerClient.LOGGER.debug("Invalid detailed time format: {}", line);
            }
            return lyricsFound;
        }

        // 如果详细格式未匹配，尝试简单时间格式 [mm:ss]
        Matcher simpleMatcher = SIMPLE_TIME_PATTERN.matcher(line);
        if (simpleMatcher.find()) {
            try {
                long timeMs = parseTimeToMs(simpleMatcher.group(1), simpleMatcher.group(2), "0");
                String lyricText = extractCleanLyricText(line, simpleMatcher.end());

                // [关键修复 2] 同样移除了 isTranslationLine 的判断。
                if (!lyricText.isEmpty()) {
                    lyrics.put(timeMs, lyricText);
                    lyricsFound++;
                }
            } catch (NumberFormatException e) {
                LaciamusicplayerClient.LOGGER.debug("Invalid simple time format: {}", line);
            }
        }

        return lyricsFound;
    }

    /**
     * 将解析出的时间字符串 (分, 秒, 毫秒) 转换为总毫秒数。
     */
    private static long parseTimeToMs(String minutesStr, String secondsStr, String millisecondsStr) {
        try {
            long minutes = Long.parseLong(minutesStr);
            long seconds = Long.parseLong(secondsStr);
            long milliseconds;

            // LRC标准中毫秒可能是2位(百分之一秒)或3位(毫秒)
            if (millisecondsStr.length() == 2) {
                milliseconds = Long.parseLong(millisecondsStr) * 10;
            } else {
                milliseconds = Long.parseLong(millisecondsStr);
            }

            return (minutes * 60 + seconds) * 1000 + milliseconds;
        } catch (NumberFormatException e) {
            LaciamusicplayerClient.LOGGER.warn("Could not parse time: {}:{}.{}", minutesStr, secondsStr, millisecondsStr);
            return 0; // 解析失败返回0
        }
    }

    /**
     * 从时间标签后提取干净的歌词文本。
     */
    private static String extractCleanLyricText(String line, int textStart) {
        if (textStart >= line.length()) {
            return "";
        }
        String text = line.substring(textStart).trim();
        // 兼容一些不规范的格式，比如时间标签后还有一个 ']'
        if (text.startsWith("]")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    /**
     * 检查是否是元数据行，如 [ti:] [ar:] [al:] [by:] 等。
     */
    private static boolean isMetadataLine(String line) {
        return line.startsWith("[ar:") || line.startsWith("[ti:") || line.startsWith("[al:") ||
                line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[re:") ||
                line.startsWith("[ve:") || line.startsWith("[la:");
    }

    /**
     * 根据当前播放时间，从歌词列表中获取应显示的当前歌词。
     */
    public static String getCurrentLyric(TreeMap<Long, String> lyrics, long currentTime) {
        if (lyrics == null || lyrics.isEmpty() || currentTime < 0) {
            return "";
        }
        try {
            // floorEntry会找到小于或等于当前时间的最大键值对，这正是歌词显示逻辑
            java.util.Map.Entry<Long, String> entry = lyrics.floorEntry(currentTime);
            if (entry != null) {
                return entry.getValue();
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Error getting current lyric", e);
        }
        return ""; // 如果没有找到或发生异常，返回空字符串
    }
}