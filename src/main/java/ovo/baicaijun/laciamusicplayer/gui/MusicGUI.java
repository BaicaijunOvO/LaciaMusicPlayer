package ovo.baicaijun.laciamusicplayer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.MusicData;
import ovo.baicaijun.laciamusicplayer.music.MusicManager;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author BaicaijunOvO
 * @modified 修复了渲染顺序和点击其他区域时输入框不会失去焦点的问题，支持超长Cookie输入
 **/
public class MusicGUI extends Screen {
    // --- 布局常量 ---
    private static final int LIST_WIDTH = 200;
    private static final int ITEM_HEIGHT = 20;
    private static final int VISIBLE_ITEMS = 15;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

//    // --- GUI 控件 ---
//    private TextFieldWidget cookieScrollableField; // 可滚动的长Cookie输入框
    private static final int CONTROL_X = LIST_WIDTH + 20;
    private static final int PLAY_BUTTON_Y = 40;
    private static final int PAUSE_BUTTON_Y = 70;
    private static final int STOP_BUTTON_Y = 100;
    private static final int PREV_BUTTON_Y = 140;
    private static final int NEXT_BUTTON_Y = 140;
    private static final int MODE_BUTTON_Y = 170;
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
    private float volume = 0.5f;
    private boolean playButtonHovered, pauseButtonHovered, stopButtonHovered;
    private boolean prevButtonHovered, nextButtonHovered, modeButtonHovered;
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

//        // 创建可滚动的Cookie输入框
//        this.cookieScrollableField = new TextFieldWidget(this.textRenderer, CONTROL_X, COOKIE_FIELD_Y,
//                COOKIE_FIELD_WIDTH, COOKIE_FIELD_HEIGHT, Text.literal("输入网易云音乐Cookie"));
//        this.cookieScrollableField.setText(LaciamusicplayerClient.cookies);
//        this.cookieScrollableField.setMaxLength(Integer.MAX_VALUE);
//        this.cookieScrollableField.setEditable(true);
//
//        // 添加文本框变化监听器
//        this.cookieScrollableField.setChangedListener(text -> {
//            LaciamusicplayerClient.cookies = text;
//            NeteaseMusicLoader.setCookie(text);
//        });
    }

    @Override
    public void close() {
//        String currentCookieText = this.cookieScrollableField.getText();
//        LaciamusicplayerClient.cookies = currentCookieText;
//        NeteaseMusicLoader.setCookie(currentCookieText);
        super.close();
    }

//    private void renderCookieLengthHint(DrawContext context) {
//        String cookieText = this.cookieScrollableField.getText();
//        int length = cookieText.length();
//        String lengthText = "长度: " + length + " 字符";
//
//        int textColor;
//        if (length < 100) {
//            textColor = 0xFFFF5555;
//        } else if (length < 500) {
//            textColor = 0xFFFFFF55;
//        } else {
//            textColor = 0xFF55FF55;
//        }
//
//        context.drawText(this.textRenderer, lengthText,
//                CONTROL_X + COOKIE_FIELD_WIDTH + 30, COOKIE_FIELD_Y + 5, textColor, false);
//
//        String expandText = cookieFieldExpanded ? "▲" : "▼";
//        context.drawText(this.textRenderer, expandText,
//                CONTROL_X + COOKIE_FIELD_WIDTH + 5, COOKIE_FIELD_Y + 5, 0xFFFFFFFF, false);
//    }
//
//    private void renderCookieLabel(DrawContext context) {
//        context.drawText(this.textRenderer, "网易云音乐Cookie:", CONTROL_X, COOKIE_FIELD_Y - 15, 0xFFFFFFFF, false);
//        String helpText = "格式: key1=value1; key2=value2; ...";
//        context.drawText(this.textRenderer, helpText, CONTROL_X, COOKIE_FIELD_Y - 25, 0xFFAAAAAA, false);
//    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        updateButtonHoverState(mouseX, mouseY);
        renderTitleBar(context);
        renderMusicList(context);
        renderControlPanel(context);
        renderCustomButtons(context);
        //renderCookieLabel(context);
        renderVolumeSlider(context);
        renderSongDetails(context);
        //this.cookieScrollableField.render(context, mouseX, mouseY, delta);
        //renderCookieLengthHint(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
//        if (this.cookieScrollableField.mouseClicked(mouseX, mouseY, button)) {
//            setFocused(this.cookieScrollableField);
//            return true;
//        }
//
//        setFocused(null);
//        if (this.cookieScrollableField.isFocused()) {
//            this.cookieScrollableField.setFocused(false);
//        }

        if (mouseX >= 0 && mouseX < LIST_WIDTH && mouseY >= 35 && mouseY < this.height) {
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

//        if (isPointInRect((int) mouseX, (int) mouseY, CONTROL_X + COOKIE_FIELD_WIDTH + 5, COOKIE_FIELD_Y, 20, 20)) {
//            cookieFieldExpanded = !cookieFieldExpanded;
//            if (cookieFieldExpanded) {
//                this.cookieScrollableField.setHeight(120);
//            } else {
//                this.cookieScrollableField.setHeight(COOKIE_FIELD_HEIGHT);
//            }
//            return true;
//        }

        if (isPointInRect((int) mouseX, (int) mouseY, CONTROL_X, VOLUME_SLIDER_Y, VOLUME_SLIDER_WIDTH, VOLUME_SLIDER_HEIGHT)) {
            volumeSliderDragging = true;
            updateVolumeFromMouseX((int) mouseX);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
//        if (this.cookieScrollableField.isFocused()) {
//            if (keyCode == 256) {
//                this.cookieScrollableField.setFocused(false);
//                setFocused(null);
//                return true;
//            }
//            if (keyCode == 257) {
//                String currentText = this.cookieScrollableField.getText();
//                this.cookieScrollableField.setText(currentText + "\n");
//                return true;
//            }
//            return this.cookieScrollableField.keyPressed(keyCode, scanCode, modifiers);
//        }

        switch (keyCode) {
            case 256:
                this.close();
                return true;
            case 264:
                if (selectedIndex < musicNames.size() - 1) {
                    selectedIndex++;
                    if (selectedIndex >= scrollOffset + VISIBLE_ITEMS) scrollOffset++;
                }
                return true;
            case 265:
                if (selectedIndex > 0) {
                    selectedIndex--;
                    if (selectedIndex < scrollOffset) scrollOffset--;
                }
                return true;
            case 257:
                if (selectedIndex != -1) playSongByIndex(selectedIndex);
                return true;
            case 67:
                if (hasControlDown()) {
                    LaciamusicplayerClient.LOGGER.info("Cookie已复制到剪贴板");
                    return true;
                }
                break;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        context.fill(0, 30, LIST_WIDTH, this.height, 0x66222222);
        context.drawBorder(0, 30, LIST_WIDTH, this.height - 30, 0xFF555555);
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

    private void renderControlPanel(DrawContext context) {
        context.fill(LIST_WIDTH + 10, 30, this.width - 10, this.height, 0x66222222);
        context.drawBorder(LIST_WIDTH + 10, 30, this.width - LIST_WIDTH - 20, this.height - 30, 0xFF555555);
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
        int detailY = 210;
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

    private void drawCustomButton(DrawContext context, int x, int y, int width, int height, String text, boolean hovered) {
        int bgColor = hovered ? 0xFF555555 : 0xFF333333;
        context.fill(x, y, x + width, y + height, bgColor);
        context.drawBorder(x, y, width, height, hovered ? 0xFFAAAAAA : 0xFF888888);
        context.drawText(this.textRenderer, text, x + (width - this.textRenderer.getWidth(text)) / 2, y + (height - 8) / 2, 0xFFFFFFFF, false);
    }

    private void drawScrollBar(DrawContext context) {
        if (musicNames.size() <= VISIBLE_ITEMS) return;
        int scrollBarWidth = 8;
        int scrollBarX = LIST_WIDTH - scrollBarWidth - 2;
        int scrollBarY = 32;
        int scrollBarHeight = this.height - 34;
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
        if (volumeSliderDragging) updateVolumeFromMouseX(mouseX);
    }

    private void updateVolumeFromMouseX(int mouseX) {
        volume = Math.max(0, Math.min(1, (float) (mouseX - (CONTROL_X)) / VOLUME_SLIDER_WIDTH));
        MusicPlayer.volume = volume;
        if (musicPlayer != null) musicPlayer.setVolume(volume);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (volumeSliderDragging) {
            volumeSliderDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (volumeSliderDragging) {
            updateVolumeFromMouseX((int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < LIST_WIDTH) {
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