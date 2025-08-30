package ovo.baicaijun.laciamusicplayer.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 歌词渲染器 - 完全重写以确保可靠切换
 */
public class LyricRenderer {

    public static Boolean Visible = true;

    // 使用原子引用确保线程安全
    private static final AtomicReference<LyricState> currentLyric = new AtomicReference<>(new LyricState("", 0));
    private static long lastRenderTime = 0;

    /**
     * 歌词状态类
     */
    private static class LyricState {
        public final String text;
        public final long startTime;
        public final long position;

        public LyricState(String text, long position) {
            this.text = text != null ? text : "";
            this.startTime = System.currentTimeMillis();
            this.position = position;
        }
    }

    /**
     * 当歌词变化时调用
     */
    public static void onLyricChanged(String newLyric, long position) {
        if (newLyric != null && !newLyric.trim().isEmpty()) {
            currentLyric.set(new LyricState(newLyric, position));
            LaciamusicplayerClient.LOGGER.debug("LyricRenderer: New lyric set - '{}' at {}ms", newLyric, position);
        }
    }

    /**
     * 渲染歌词到屏幕
     */
    public static void renderLyrics(DrawContext context) {
        if (!Visible) return;
        MusicPlayer musicPlayer = LaciamusicplayerClient.musicPlayer;

        // 检查是否应该显示歌词
        if (!shouldShowLyrics(musicPlayer)) {
            return;
        }

        LyricState lyric = currentLyric.get();
        long currentTime = System.currentTimeMillis();
        lastRenderTime = currentTime;

        // 计算歌词显示时间
        long displayTime = currentTime - lyric.startTime;

        // 计算透明度（淡入淡出效果）
        float alpha = calculateAlpha(displayTime);
        if (alpha < 0.01f) {
            return;
        }

        // 渲染歌词
        renderLyricText(context, lyric.text, alpha);

        // 调试信息（每秒记录一次）
        if (currentTime % 1000 < 16) { // 约每秒一次
            LaciamusicplayerClient.LOGGER.debug("Rendering lyric: '{}' (alpha: {}, displayTime: {}ms)",
                    lyric.text, alpha, displayTime);
        }
    }

    /**
     * 检查是否应该显示歌词
     */
    private static boolean shouldShowLyrics(MusicPlayer musicPlayer) {
        if (musicPlayer == null || !musicPlayer.isPlaying()) {
            return false;
        }

        LyricState lyric = currentLyric.get();
        if (lyric.text.isEmpty()) {
            return false;
        }

        // 检查歌词是否过期（显示超过10秒）
        long displayTime = System.currentTimeMillis() - lyric.startTime;
        if (displayTime > 10000) {
            return false;
        }

        return true;
    }

    /**
     * 计算透明度
     */
    private static float calculateAlpha(long displayTime) {
        // 淡入阶段（前0.5秒）
        if (displayTime < 500) {
            return displayTime / 500.0f;
        }

        // 稳定显示阶段（0.5秒到8秒）
        if (displayTime < 8000) {
            return 1.0f;
        }

        // 淡出阶段（8秒到10秒）
        if (displayTime < 10000) {
            return 1.0f - (displayTime - 8000) / 2000.0f;
        }

        // 超过10秒完全透明
        return 0.0f;
    }

    /**
     * 渲染歌词文本
     */
    private static void renderLyricText(DrawContext context, String lyric, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // 计算位置（物品栏上方）
        int yPos = screenHeight - 65;

        // 计算文本宽度
        int textWidth = textRenderer.getWidth(lyric);
        int xPos = (screenWidth - textWidth) / 2;

        // 绘制半透明背景
        int bgAlpha = (int)(150 * alpha);
        int bgColor = new Color(255, 255, 255,bgAlpha).getRGB();
        context.fill(xPos - 5, yPos - 2, xPos + textWidth + 5, yPos + 10, bgColor);

        // 绘制歌词文本
        int textColor = getColorWithAlpha(0xFFFFFF, alpha);
        context.drawText(textRenderer, Text.literal(lyric), xPos, yPos, textColor, true);

        // 绘制进度指示器
        long displayTime = System.currentTimeMillis() - currentLyric.get().startTime;
        float progress = Math.min(displayTime / 10000.0f, 1.0f);
        int progressWidth = (int)(textWidth * progress);
        int progressColor = getColorWithAlpha(0xFF00FF00, alpha);
        context.fill(xPos, yPos + 9, xPos + progressWidth, yPos + 10, progressColor);
    }

    /**
     * 为颜色添加透明度
     */
    private static int getColorWithAlpha(int rgb, float alpha) {
        int a = (int)(255 * alpha);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 重置歌词显示
     */
    public static void reset() {
        currentLyric.set(new LyricState("", 0));
        LaciamusicplayerClient.LOGGER.debug("LyricRenderer reset");
    }
    public static void toggleVisible() {
        setVisible(!Visible);
    }

    public static void setVisible(Boolean visible) {
        Visible = visible;
    }

    public static Boolean getVisible() {
        return Visible;
    }
}