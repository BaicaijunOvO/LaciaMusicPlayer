package ovo.baicaijun.laciamusicplayer.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.MusicData;
import ovo.baicaijun.laciamusicplayer.music.MusicManager;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author BaicaijunOvO
 * @modified 修复了渲染顺序和点击其他区域时输入框不会失去焦点的问题，支持超长Cookie输入，添加了歌词显示和进度条功能
 **/
public class MusicGUI extends Screen {
    // --- 布局常量 ---
    private static final int LIST_WIDTH = 200;
    private static final int ITEM_HEIGHT = 20;
    private static final int VISIBLE_ITEMS = 15;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_PANEL_HEIGHT = 80; // 增加底部面板高度以容纳进度条

    //    // --- GUI 控件 ---
//    private TextFieldWidget cookieScrollableField; // 可滚动的长Cookie输入框
    private static final int CONTROL_X = LIST_WIDTH + 20;
    private static final int PLAY_BUTTON_Y = 40;
    private static final int PAUSE_BUTTON_Y = 70;
    private static final int STOP_BUTTON_Y = 100;
    private static final int PREV_BUTTON_Y = 140;
    private static final int NEXT_BUTTON_Y = 140;
    private static final int MODE_BUTTON_Y = 170;
    private static final int LYRICS_BUTTON_Y = 200; // 新增歌词按钮位置
    private static final int COOKIE_FIELD_Y = 320;
    private static final int COOKIE_FIELD_WIDTH = 300;
    private static final int COOKIE_FIELD_HEIGHT = 60;
    private static final int VOLUME_SLIDER_Y = 400;
    private static final int VOLUME_SLIDER_WIDTH = 150;
    private static final int VOLUME_SLIDER_HEIGHT = 10;
    private static final MusicPlayer musicPlayer = LaciamusicplayerClient.musicPlayer;
    // --- 成员变量 ---
    private static final List<String> musicNames = new ArrayList<>();
    private static final HashMap<String, String> musicRealName = new HashMap<>();
    private static int selectedIndex = -1;
    private final Random random = new Random();
    private int scrollOffset = 0;
    // --- 状态变量 ---
    private boolean volumeSliderDragging = false;
    private boolean progressSliderDragging = false; // 新增进度条拖动状态
    private float volume = 0.5f;
    private boolean playButtonHovered, pauseButtonHovered, stopButtonHovered;
    private boolean prevButtonHovered, nextButtonHovered, modeButtonHovered;
    private boolean lyricsButtonHovered; // 新增歌词按钮悬停状态
    private final boolean cookieFieldExpanded = false;

    public MusicGUI() {
        super(Text.literal("LaciaMusicPlayer"));
    }

    private static void playSongByIndex(int index) {
        if (index >= 0 && index < musicNames.size()) {
            selectedIndex = index;
            String musicTitle = musicNames.get(selectedIndex);
            String realName = musicRealName.get(musicTitle);
            MusicData musicData = MusicManager.musics.get(realName);
            if (musicData != null && musicPlayer != null) {
                musicPlayer.load(musicData.getFile(), null, musicData);
                musicPlayer.play();
            }
        }
    }

    public static void playNext() {
        if (musicNames.isEmpty()) return;
        playSongByIndex((selectedIndex + 1) % musicNames.size());
    }

    public static void playPrevious() {
        if (musicNames.isEmpty()) return;
        playSongByIndex((selectedIndex - 1 + musicNames.size()) % musicNames.size());
    }

    @Override
    protected void init() {
        super.init();
        musicNames.clear();
        musicRealName.clear();
        MusicManager.musics.forEach((name, data) -> {
            musicNames.add(data.getTitle());
            musicRealName.put(data.getTitle(), name);
        });
        Collections.sort(musicNames);

        if (musicPlayer != null) {
            volume = musicPlayer.getVolume();
            musicPlayer.setOnSongEndCallback(this::handleSongEnd);
        }
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        updateButtonHoverState(mouseX, mouseY);
        renderTitleBar(context);
        renderMusicList(context);
        renderControlPanel(context);
        renderCustomButtons(context);
        renderVolumeSlider(context);
        renderSongDetails(context);
        renderBottomPanel(context, mouseX, mouseY); // 新增底部面板渲染
    }


    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (mouseX >= 0 && mouseX < LIST_WIDTH && mouseY >= 35 && mouseY < this.height - BOTTOM_PANEL_HEIGHT) {
            int index = scrollOffset + (int) ((mouseY - 35) / ITEM_HEIGHT);
            if (index >= 0 && index < musicNames.size()) {
                if (!(selectedIndex == index && musicPlayer.isPlaying())) {
                    playSongByIndex(index);
                }
            }
            return true;
        }

        if (playButtonHovered) {
            if (selectedIndex != -1) musicPlayer.play();
            return true;
        }
        if (pauseButtonHovered) {
            musicPlayer.pause();
            return true;
        }
        if (stopButtonHovered) {
            musicPlayer.stop();
            return true;
        }
        if (prevButtonHovered) {
            playPrevious();
            return true;
        }
        if (nextButtonHovered) {
            playNext();
            return true;
        }
        if (modeButtonHovered) {
            musicPlayer.togglePlaybackMode();
            return true;
        }
        if (lyricsButtonHovered) {
            LyricRenderer.toggleVisible();
            MessageUtil.sendMessage("§a歌词显示: " +
                    (LyricRenderer.getVisible() ? "开启" : "关闭"));
            return true;
        }

        if (isPointInRect((int) mouseX, (int) mouseY, CONTROL_X, VOLUME_SLIDER_Y, VOLUME_SLIDER_WIDTH, VOLUME_SLIDER_HEIGHT)) {
            volumeSliderDragging = true;
            updateVolumeFromMouseX((int) mouseX);
            return true;
        }

        // 进度条点击处理
        if (musicPlayer != null && musicPlayer.isPlaying()) {
            int progressX = 10;
            int progressY = this.height - BOTTOM_PANEL_HEIGHT + 60;
            int progressWidth = this.width - 20;
            if (isPointInRect((int) mouseX, (int) mouseY, progressX, progressY, progressWidth, 8)) {
                progressSliderDragging = true;
                updateProgressFromMouseX((int) mouseX);
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        switch (keyCode) {
            case 256: // ESC
                this.close();
                return true;
            case 264: // DOWN
                if (selectedIndex < musicNames.size() - 1) {
                    selectedIndex++;
                    if (selectedIndex >= scrollOffset + VISIBLE_ITEMS) scrollOffset++;
                }
                return true;
            case 265: // UP
                if (selectedIndex > 0) {
                    selectedIndex--;
                    if (selectedIndex < scrollOffset) scrollOffset--;
                }
                return true;
            case 257: // ENTER
                if (selectedIndex != -1) playSongByIndex(selectedIndex);
                return true;
//            case 67: // C
//                if (hasControlDown()) {
//                    LaciamusicplayerClient.LOGGER.info("Cookie已复制到剪贴板");
//                    return true;
//                }
//                break;
        }
        return super.keyPressed(input);
    }



    private void handleSongEnd() {
        if (musicPlayer == null || musicNames.isEmpty()) return;
        switch (musicPlayer.getPlaybackMode()) {
            case SINGLE_LOOP:
                playSongByIndex(selectedIndex);
                break;
            case SHUFFLE:
                playSongByIndex(random.nextInt(musicNames.size()));
                break;
            case LIST_LOOP:
            default:
                playNext();
                break;
        }
    }

    private void renderTitleBar(DrawContext context) {
        context.fill(0, 0, this.width, 30, 0xCC333333);
        context.drawText(this.textRenderer, "LaciaMusicPlayer - 音乐库 (" + musicNames.size() + " 首)", 10, 10, Color.WHITE.getRGB(), false);
    }

    private void renderMusicList(DrawContext context) {
        context.fill(0, 30, LIST_WIDTH, this.height - BOTTOM_PANEL_HEIGHT, 0x66222222);
        // 使用 drawHorizontalLine 和 drawVerticalLine 替代 drawBorder
        drawBorder(context, 0, 30, LIST_WIDTH, this.height - BOTTOM_PANEL_HEIGHT - 30, 0xFF555555);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, musicNames.size() - VISIBLE_ITEMS)));
        int startY = 35;
        for (int i = scrollOffset; i < Math.min(musicNames.size(), scrollOffset + VISIBLE_ITEMS); i++) {
            if (i < 0 || i >= musicNames.size()) continue;
            int y = startY + (i - scrollOffset) * ITEM_HEIGHT;
            if (i == selectedIndex) {
                context.fill(2, y, LIST_WIDTH - 2, y + ITEM_HEIGHT, 0x664477AA);
            }
            String displayName = musicNames.get(i);
            displayName = this.textRenderer.trimToWidth(displayName, LIST_WIDTH - 15);
            context.drawText(this.textRenderer, displayName, 5, y + 5, Color.WHITE.getRGB(), false);
        }
        if (musicNames.size() > VISIBLE_ITEMS) drawScrollBar(context);
    }

    // 自定义边框绘制方法，替代 drawBorder
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        // 上边框
        context.fill(x, y, x + width, y + 1, color);
        // 下边框
        context.fill(x, y + height - 1, x + width, y + height, color);
        // 左边框
        context.fill(x, y, x + 1, y + height, color);
        // 右边框
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void renderControlPanel(DrawContext context) {
        context.fill(LIST_WIDTH + 10, 30, this.width - 10, this.height - BOTTOM_PANEL_HEIGHT, 0x66222222);
        drawBorder(context, LIST_WIDTH + 10, 30, this.width - LIST_WIDTH - 20, this.height - BOTTOM_PANEL_HEIGHT - 30, 0xFF555555);
    }

    private void renderCustomButtons(DrawContext context) {
        drawCustomButton(context, CONTROL_X, PLAY_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, musicPlayer.isPaused() ? "继续" : "播放", playButtonHovered);
        drawCustomButton(context, CONTROL_X, PAUSE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "暂停", pauseButtonHovered);
        drawCustomButton(context, CONTROL_X, STOP_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "停止", stopButtonHovered);
        drawCustomButton(context, CONTROL_X, PREV_BUTTON_Y, 35, BUTTON_HEIGHT, "<<", prevButtonHovered);
        drawCustomButton(context, CONTROL_X + BUTTON_WIDTH - 35, NEXT_BUTTON_Y, 35, BUTTON_HEIGHT, ">>", nextButtonHovered);
        String modeText = "模式";
        if (musicPlayer != null) {
            switch (musicPlayer.getPlaybackMode()) {
                case LIST_LOOP:
                    modeText = "列表循环";
                    break;
                case SINGLE_LOOP:
                    modeText = "单曲循环";
                    break;
                case SHUFFLE:
                    modeText = "随机播放";
                    break;
            }
        }
        drawCustomButton(context, CONTROL_X, MODE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, modeText, modeButtonHovered);
        drawCustomButton(context, CONTROL_X, LYRICS_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, "歌词", lyricsButtonHovered);
    }

    private void drawCustomButton(DrawContext context, int x, int y, int width, int height, String text, boolean hovered) {
        int bgColor = hovered ? 0xFF555555 : 0xFF333333;
        context.fill(x, y, x + width, y + height, bgColor);
        drawBorder(context, x, y, width, height, hovered ? 0xFFAAAAAA : 0xFF888888);
        context.drawText(this.textRenderer, text, x + (width - this.textRenderer.getWidth(text)) / 2, y + (height - 8) / 2, 0xFFFFFFFF, false);
    }

    private void renderVolumeSlider(DrawContext context) {
        context.drawText(this.textRenderer, "音量:", CONTROL_X, VOLUME_SLIDER_Y - 15, 0xFFFFFFFF, false);
        context.fill(CONTROL_X, VOLUME_SLIDER_Y, CONTROL_X + VOLUME_SLIDER_WIDTH, VOLUME_SLIDER_Y + VOLUME_SLIDER_HEIGHT, 0xFF444444);
        int fillWidth = (int) (VOLUME_SLIDER_WIDTH * volume);
        context.fill(CONTROL_X, VOLUME_SLIDER_Y, CONTROL_X + fillWidth, VOLUME_SLIDER_Y + VOLUME_SLIDER_HEIGHT, 0xFF4477CC);
        int handleX = CONTROL_X + fillWidth - 3;
        context.fill(handleX, VOLUME_SLIDER_Y - 2, handleX + 6, VOLUME_SLIDER_Y + VOLUME_SLIDER_HEIGHT + 2, 0xFFFFFFFF);
        String volumeText = (int) (volume * 100) + "%";
        context.drawText(this.textRenderer, volumeText, CONTROL_X + VOLUME_SLIDER_WIDTH + 5, VOLUME_SLIDER_Y - 2, 0xFFFFFFFF, false);
    }

    private void renderSongDetails(DrawContext context) {
        int detailX = LIST_WIDTH + 20;
        int detailY = 280;
        context.drawText(this.textRenderer, "歌曲信息:", detailX, detailY, 0xFFFFFFAA, false);
        if (selectedIndex >= 0 && selectedIndex < musicNames.size()) {
            String selectedMusic = musicNames.get(selectedIndex);
            String realName = musicRealName.get(selectedMusic);
            MusicData musicData = MusicManager.musics.get(realName);
            if (musicData != null) {
                context.drawText(this.textRenderer, "标题: " + this.textRenderer.trimToWidth(musicData.getTitle(), this.width - detailX - 15), detailX, detailY + 20, 0xFFFFFFFF, false);
                context.drawText(this.textRenderer, "歌手: " + this.textRenderer.trimToWidth(musicData.getArtist(), this.width - detailX - 15), detailX, detailY + 35, 0xFFFFFFFF, false);
                context.drawText(this.textRenderer, "专辑: " + this.textRenderer.trimToWidth(musicData.getAlbum(), this.width - detailX - 15), detailX, detailY + 50, 0xFFFFFFFF, false);
            }
        } else {
            context.drawText(this.textRenderer, "未选中音乐", detailX, detailY + 20, 0xFFFF5555, false);
        }
    }

    // 新增底部面板渲染方法
    private void renderBottomPanel(DrawContext context, int mouseX, int mouseY) {
        int panelY = this.height - BOTTOM_PANEL_HEIGHT;
        context.fill(0, panelY, this.width, this.height, 0xCC333333);

        // 显示播放状态信息
        if (musicPlayer != null) {
            String status;
            if (musicPlayer.isPlaying()) {
                MusicData currentSong = musicPlayer.getCurrentMusicData();
                LaciamusicplayerClient.LOGGER.info("正在播放: " + currentSong.getTitle());
                status = (currentSong != null) ? "正在播放: " + currentSong.getTitle() + " - " + currentSong.getArtist() : "正在播放: 未知歌曲";
                context.drawText(this.textRenderer, status, 10, panelY + 10, 0xFF00FF00, false);
            } else if (musicPlayer.isPaused()) {
                context.drawText(this.textRenderer, "已暂停", 10, panelY + 10, 0xFFFFFF00, false);
            }
        }

        // 渲染进度条
        if (musicPlayer != null && musicPlayer.isPlaying()) {
            int progressX = 10;
            int progressY = panelY + 60;
            long duration = musicPlayer.getDuration();
            long elapsed = musicPlayer.getElapsedTime();

            context.fill(progressX, progressY, progressX + this.width - 20, progressY + 8, 0xFF444444);

            if (duration > 0) {
                int progressWidth = (int) ((this.width - 20) * elapsed / duration);
                context.fill(progressX, progressY, progressX + progressWidth, progressY + 8, 0xFF44AA44);
            }

            String timeText = formatTime(elapsed * 1000) + " / " + formatTime(duration * 1000);
            context.drawText(this.textRenderer, timeText, progressX, progressY + 12, 0xFFFFFFFF, false);
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void drawScrollBar(DrawContext context) {
        if (musicNames.size() <= VISIBLE_ITEMS) return;
        int scrollBarWidth = 8;
        int scrollBarX = LIST_WIDTH - scrollBarWidth - 2;
        int scrollBarY = 32;
        int scrollBarHeight = this.height - BOTTOM_PANEL_HEIGHT - 34;
        context.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0x66444444);
        float scrollPercentage = (float) scrollOffset / Math.max(1, musicNames.size() - VISIBLE_ITEMS);
        int scrollThumbHeight = Math.max(20, (int) (scrollBarHeight * ((float) VISIBLE_ITEMS / musicNames.size())));
        int scrollThumbY = scrollBarY + (int) (scrollPercentage * (scrollBarHeight - scrollThumbHeight));
        context.fill(scrollBarX, scrollThumbY, scrollBarX + scrollBarWidth, scrollThumbY + scrollThumbHeight, 0xCC888888);
    }

    private void updateButtonHoverState(int mouseX, int mouseY) {
        playButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, PLAY_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        pauseButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, PAUSE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        stopButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, STOP_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        prevButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, PREV_BUTTON_Y, 35, BUTTON_HEIGHT);
        nextButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X + BUTTON_WIDTH - 35, NEXT_BUTTON_Y, 35, BUTTON_HEIGHT);
        modeButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, MODE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        lyricsButtonHovered = isPointInRect(mouseX, mouseY, CONTROL_X, LYRICS_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);

        if (volumeSliderDragging) updateVolumeFromMouseX(mouseX);
        if (progressSliderDragging) updateProgressFromMouseX(mouseX);
    }

    private void updateVolumeFromMouseX(int mouseX) {
        volume = Math.max(0, Math.min(1, (float) (mouseX - (CONTROL_X)) / VOLUME_SLIDER_WIDTH));
        MusicPlayer.volume = volume;
        if (musicPlayer != null) musicPlayer.setVolume(volume);
    }

    // 新增进度条更新方法
    private void updateProgressFromMouseX(int mouseX) {
        if (musicPlayer != null && musicPlayer.isPlaying()) {
            int progressX = 10;
            int progressWidth = this.width - 20;
            float progress = (float) (mouseX - progressX) / progressWidth;
            progress = Math.max(0, Math.min(1, progress));

            long duration = musicPlayer.getDuration();
            long seekPosition = (long) (duration * progress);
            musicPlayer.seek(seekPosition);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (volumeSliderDragging) {
            volumeSliderDragging = false;
            return true;
        }
        if (progressSliderDragging) {
            progressSliderDragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        if (volumeSliderDragging) {
            updateVolumeFromMouseX((int) mouseX);
            return true;
        }
        if (progressSliderDragging) {
            updateProgressFromMouseX((int) mouseX);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }



    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < LIST_WIDTH && mouseY < this.height - BOTTOM_PANEL_HEIGHT) {
            if (verticalAmount > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else scrollOffset = Math.min(Math.max(0, musicNames.size() - VISIBLE_ITEMS), scrollOffset + 1);
            return true;
        }
        return false;
    }

    private boolean isPointInRect(int x, int y, int rectX, int rectY, int rectWidth, int rectHeight) {
        return x >= rectX && x <= rectX + rectWidth && y >= rectY && y <= rectY + rectHeight;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}