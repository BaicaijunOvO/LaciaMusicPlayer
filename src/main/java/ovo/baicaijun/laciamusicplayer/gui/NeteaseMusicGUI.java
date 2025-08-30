package ovo.baicaijun.laciamusicplayer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.MusicData;
import ovo.baicaijun.laciamusicplayer.music.MusicListData;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;
import ovo.baicaijun.laciamusicplayer.music.netease.NeteaseMusicLoader;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author BaicaijunOvO
 * @description 网易云音乐播放器UI，支持歌单和在线音乐播放
 */
public class NeteaseMusicGUI extends Screen {
    // --- 数据 ---
    private List<MusicListData> playlists = new ArrayList<>();
    private List<MusicListData> recommendPlaylists = new ArrayList<>();
    // [修复] 新增一个稳定的合并列表，用于UI渲染和交互，避免异步导致的数据不一致
    private List<MusicListData> allPlaylists = new ArrayList<>();
    private List<MusicData> currentSongList = new ArrayList<>();
    private String selectedPlaylistName = null;
    private int selectedPlaylistIndex = -1;

    // --- 缓存 ---
    private Map<Long, List<MusicData>> playlistSongsCache = new HashMap<>(); // 歌单歌曲缓存
    private List<MusicData> dailySongsCache = null; // 每日推荐歌曲缓存
    private long lastDailySongsLoadTime = 0; // 上次加载每日推荐的时间
    private static final long DAILY_SONGS_CACHE_TIME = 30 * 60 * 1000; // 30分钟缓存时间

    // --- 状态 ---
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private boolean volumeSliderDragging = false;
    private boolean progressSliderDragging = false;
    private float volume = 0.5f;

    // --- 布局常量 ---
    private static final int LEFT_PANEL_WIDTH = 150;
    private static final int BOTTOM_PANEL_HEIGHT = 80; // 增加高度以容纳进度条
    private static final int ITEM_HEIGHT = 20;
    private static final int PADDING = 10;

    // --- 按钮悬停状态 ---
    private boolean playButtonHovered, pauseButtonHovered, stopButtonHovered;
    private boolean prevButtonHovered, nextButtonHovered, modeButtonHovered;
    private boolean lyricsButtonHovered;

    // --- 新增字段 ---
    private int maxVisibleLeftItems = 0;
    private int maxVisibleRightItems = 0;

    private static final MusicPlayer musicPlayer = LaciamusicplayerClient.musicPlayer;

    public NeteaseMusicGUI() {
        super(Text.literal("网易云音乐"));
    }

    @Override
    protected void init() {
        super.init();

        if (NeteaseMusicLoader.cookie == null || LaciamusicplayerClient.cookies == null){
            MessageUtil.sendMessage("请先使用 *music qrcode 登录网易云账号");
            close();
        }

        // 加载网易云音乐歌单和推荐内容
        loadNeteasePlaylists();
        loadRecommendations();

        if (musicPlayer != null) {
            volume = musicPlayer.getVolume();
        }
    }

    /**
     * [修复] 新增方法：重建用于UI的合并歌单列表
     * 这个方法确保了UI总是有个一致的数据源
     */
    private void rebuildCombinedPlaylists() {
        allPlaylists.clear();

        // 添加每日推荐（特殊项）
        allPlaylists.add(new MusicListData("每日推荐", -1, "网易云音乐"));

        // 添加推荐歌单
        if (!recommendPlaylists.isEmpty()) {
            allPlaylists.add(new MusicListData("--- 推荐歌单 ---", -2, ""));
            allPlaylists.addAll(recommendPlaylists);
        }

        // 添加用户歌单
        if (!playlists.isEmpty()) {
            allPlaylists.add(new MusicListData("--- 我的歌单 ---", -3, ""));
            allPlaylists.addAll(playlists);
        }
    }


    /**
     * 加载网易云音乐歌单
     */
    private void loadNeteasePlaylists() {
        playlists.clear();
        currentSongList.clear();

        // 异步加载歌单
        new Thread(() -> {
            try {
                long userID = NeteaseMusicLoader.getUserID();
                if (userID == 0) {
                    MessageUtil.sendMessage("无法获取用户ID，请使用 *music qrcode指令登录或检查Cookie设置");
                    return;
                }

                List<MusicListData> loadedPlaylists = NeteaseMusicLoader.getMusicList(userID);
                if (loadedPlaylists != null && !loadedPlaylists.isEmpty()) {
                    // 在主线程更新UI
                    if (this.client != null) {
                        this.client.execute(() -> {
                            playlists.addAll(loadedPlaylists);
                            rebuildCombinedPlaylists(); // [修复] 数据更新后，重建UI列表
                            // [修复] 移除自动加载第一个歌单的逻辑
                            // if (!playlists.isEmpty()) {
                            //     // 默认选中第一个歌单的逻辑可以保持或调整
                            //     selectedPlaylistName = playlists.get(0).getTitle();
                            //     loadPlaylistSongs(playlists.get(0).getId());
                            // }
                        });
                    }
                }
            } catch (Exception e) {
                MessageUtil.sendMessage("加载网易云音乐歌单失败 " + e);
            }
        }, "NeteasePlaylistLoader").start();
    }

    /**
     * 加载推荐内容
     */
    private void loadRecommendations() {
        // 加载推荐歌单
        new Thread(() -> {
            try {
                List<MusicListData> loadedRecommendPlaylists = NeteaseMusicLoader.getRecommendMusicList();
                if (loadedRecommendPlaylists != null && !loadedRecommendPlaylists.isEmpty()) {
                    if (this.client != null) {
                        this.client.execute(() -> {
                            recommendPlaylists.addAll(loadedRecommendPlaylists);
                            rebuildCombinedPlaylists(); // [修复] 数据更新后，重建UI列表
                            LaciamusicplayerClient.LOGGER.info("成功加载 {} 个推荐歌单", loadedRecommendPlaylists.size());
                        });
                    }
                }
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("加载推荐歌单失败", e);
            }
        }, "RecommendPlaylistLoader").start();
    }

    /**
     * 加载每日推荐歌曲（带缓存）
     */
    private void loadDailySongs() {
        // 检查缓存是否有效
        long currentTime = System.currentTimeMillis();
        if (dailySongsCache != null && (currentTime - lastDailySongsLoadTime) < DAILY_SONGS_CACHE_TIME) {
            // 使用缓存
            if (this.client != null) {
                this.client.execute(() -> {
                    currentSongList.clear();
                    currentSongList.addAll(dailySongsCache);
                    rightScrollOffset = 0;
                    //MessageUtil.sendMessage("§a已加载每日推荐歌曲 (缓存, " + dailySongsCache.size() + "首)");
                });
            }
            return;
        }

        new Thread(() -> {
            try {
                List<MusicData> dailySongs = NeteaseMusicLoader.getRecommandListMusics();
                if (dailySongs != null && !dailySongs.isEmpty()) {
                    // 更新缓存
                    dailySongsCache = dailySongs;
                    lastDailySongsLoadTime = System.currentTimeMillis();

                    if (this.client != null) {
                        this.client.execute(() -> {
                            currentSongList.clear();
                            currentSongList.addAll(dailySongs);
                            rightScrollOffset = 0;
                            //MessageUtil.sendMessage("§a已加载每日推荐歌曲 (" + dailySongs.size() + "首)");
                        });
                    }
                }
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("加载每日推荐歌曲失败", e);
                MessageUtil.sendMessage("§c加载每日推荐歌曲失败");
            }
        }, "DailySongsLoader").start();
    }

    /**
     * 加载指定歌单的歌曲（带缓存）
     */
    private void loadPlaylistSongs(long playlistId) {
        // 检查缓存
        if (playlistSongsCache.containsKey(playlistId)) {
            List<MusicData> cachedSongs = playlistSongsCache.get(playlistId);
            if (this.client != null) {
                this.client.execute(() -> {
                    currentSongList.clear();
                    currentSongList.addAll(cachedSongs);
                    rightScrollOffset = 0;
                    //MessageUtil.sendMessage("§a已加载歌单歌曲 (缓存, " + cachedSongs.size() + "首)");
                });
            }
            return;
        }

        new Thread(() -> {
            try {
                List<MusicData> songs = NeteaseMusicLoader.getListMusics(playlistId);
                if (songs != null && !songs.isEmpty()) {
                    // 添加到缓存
                    playlistSongsCache.put(playlistId, songs);

                    if (this.client != null) {
                        this.client.execute(() -> {
                            currentSongList.clear();
                            currentSongList.addAll(songs);
                            rightScrollOffset = 0;
                            //MessageUtil.sendMessage("§a已加载歌单歌曲 (" + songs.size() + "首)");
                        });
                    }
                }
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("加载歌单歌曲失败", e);
            }
        }, "PlaylistSongsLoader").start();
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        playlistSongsCache.clear();
        dailySongsCache = null;
        lastDailySongsLoadTime = 0;
        MessageUtil.sendMessage("§a已清除歌曲缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("歌单缓存: %d, 每日推荐缓存: %s",
                playlistSongsCache.size(),
                dailySongsCache != null ? dailySongsCache.size() + "首" : "无");
    }

    // --- 渲染主方法 ---
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context,mouseX,mouseY,delta);
        updateButtonHoverState(mouseX, mouseY);

        renderLeftPanel(context, mouseX, mouseY);
        renderRightPanel(context, mouseX, mouseY);
        renderBottomPanel(context, mouseX, mouseY);

        // 显示加载状态
        if (allPlaylists.size() <= 1) { // 初始状态只有每日推荐
            context.drawCenteredTextWithShadow(this.textRenderer, "正在加载歌单...", this.width / 2, this.height / 2, Color.WHITE.getRGB());
        }
    }

    // --- 各区域渲染 ---

    private void renderLeftPanel(DrawContext context, int mouseX, int mouseY) {
        int panelHeight = this.height - BOTTOM_PANEL_HEIGHT;
        context.fill(0, 0, LEFT_PANEL_WIDTH, panelHeight, 0x66222222);
        context.drawBorder(0, 0, LEFT_PANEL_WIDTH, panelHeight, 0xFF555555);

        context.drawText(this.textRenderer, "歌单列表", PADDING, 5, 0xFFFFFF00, false);

        int startY = 25;
        int availableHeight = panelHeight - startY - PADDING;
        maxVisibleLeftItems = availableHeight / ITEM_HEIGHT;

        // 确保滚动偏移量在合理范围内
        int maxLeftScroll = Math.max(0, allPlaylists.size() - maxVisibleLeftItems);
        leftScrollOffset = Math.max(0, Math.min(leftScrollOffset, maxLeftScroll));

        for (int i = leftScrollOffset; i < Math.min(allPlaylists.size(), leftScrollOffset + maxVisibleLeftItems); i++) {
            MusicListData playlist = allPlaylists.get(i);
            int y = startY + (i - leftScrollOffset) * ITEM_HEIGHT;

            // [修复] 统一处理悬浮和选中效果
            boolean isHovered = mouseX > 2 && mouseX < LEFT_PANEL_WIDTH - 2 && mouseY > y && mouseY < y + ITEM_HEIGHT;

            // 非交互元素（分隔线）
            if (playlist.getId() <= -2) {
                context.drawText(this.textRenderer, playlist.getTitle(), PADDING, y + 6, 0xFFAAAAAA, false);
                continue;
            }

            // 交互元素（每日推荐 & 普通歌单）
            if (i == selectedPlaylistIndex) {
                context.fill(2, y, LEFT_PANEL_WIDTH - 2, y + ITEM_HEIGHT, 0x664477AA); // 选中颜色
            } else if (isHovered) {
                int hoverColor = (playlist.getId() == -1) ? 0x6644AA44 : 0x44FFFFFF; // 每日推荐用特殊悬浮色，其他用通用色
                context.fill(2, y, LEFT_PANEL_WIDTH - 2, y + ITEM_HEIGHT, hoverColor);
            }

            // 绘制文本
            if (playlist.getId() == -1) { // 每日推荐
                context.drawText(this.textRenderer, "★ " + playlist.getTitle(), PADDING, y + 6, 0xFFFFFF00, false);
            } else { // 普通歌单
                String displayName = this.textRenderer.trimToWidth(playlist.getTitle(), LEFT_PANEL_WIDTH - PADDING * 2);
                int color = playlist.getAuthor().equals("网易云音乐") ? 0xFFFFAA00 : Color.WHITE.getRGB();
                context.drawText(this.textRenderer, displayName, PADDING, y + 6, color, false);

                if (!playlist.getAuthor().isEmpty()) {
                    String creator = this.textRenderer.trimToWidth("by " + playlist.getAuthor(), LEFT_PANEL_WIDTH - PADDING * 2);
                    context.drawText(this.textRenderer, creator, PADDING, y + 16, 0xFFAAAAAA, false);
                }
            }
        }

        // 绘制左侧滚动条
        if (allPlaylists.size() > maxVisibleLeftItems) {
            drawScrollBar(context, 0, startY, LEFT_PANEL_WIDTH, availableHeight,
                    leftScrollOffset, allPlaylists.size(), maxVisibleLeftItems, true);
        }
    }


    private void renderRightPanel(DrawContext context, int mouseX, int mouseY) {
        int panelX = LEFT_PANEL_WIDTH;
        int panelWidth = this.width - panelX;
        int panelHeight = this.height - BOTTOM_PANEL_HEIGHT;
        context.fill(panelX, 0, this.width, panelHeight, 0x44222222);
        context.drawBorder(panelX, 0, panelWidth, panelHeight, 0xFF555555);

        if (currentSongList.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "选择歌单加载歌曲",
                    panelX + panelWidth / 2, panelHeight / 2, 0xFFAAAAAA);
            return;
        }

        context.drawText(this.textRenderer, "歌曲列表 (" + currentSongList.size() + "首)",
                panelX + PADDING, 10, 0xFFFFFF00, false);

        int startY = 30;
        int availableHeight = panelHeight - startY - PADDING;
        maxVisibleRightItems = availableHeight / ITEM_HEIGHT;

        // 确保滚动偏移量在合理范围内
        int maxRightScroll = Math.max(0, currentSongList.size() - maxVisibleRightItems);
        rightScrollOffset = Math.max(0, Math.min(rightScrollOffset, maxRightScroll));

        for (int i = rightScrollOffset; i < Math.min(currentSongList.size(), rightScrollOffset + maxVisibleRightItems); i++) {
            MusicData song = currentSongList.get(i);
            int y = startY + (i - rightScrollOffset) * ITEM_HEIGHT;

            boolean isCurrent = musicPlayer != null && musicPlayer.isPlaying() &&
                    musicPlayer.getCurrentMusicData() != null &&
                    musicPlayer.getCurrentMusicData().equals(song);

            if (isCurrent) {
                context.fill(panelX + 2, y, this.width - 2, y + ITEM_HEIGHT, 0x664477AA);
            }

            String title = this.textRenderer.trimToWidth((i + 1) + ". " + song.getTitle(), panelWidth - PADDING * 2 - 100);
            String artist = this.textRenderer.trimToWidth(song.getArtist(), 80);

            context.drawText(this.textRenderer, title, panelX + PADDING, y + 6,
                    isCurrent ? 0xFFFFFF00 : Color.WHITE.getRGB(), false);
            context.drawText(this.textRenderer, artist, panelX + panelWidth - 90, y + 6, 0xFFAAAAAA, false);
        }

        // 绘制右侧滚动条
        if (currentSongList.size() > maxVisibleRightItems) {
            drawScrollBar(context, panelX, startY, panelWidth, availableHeight,
                    rightScrollOffset, currentSongList.size(), maxVisibleRightItems, false);
        }
    }

    // --- 新增滚动条绘制方法 ---
    private void drawScrollBar(DrawContext context, int x, int y, int width, int height,
                               int scrollOffset, int totalItems, int visibleItems, boolean isLeftPanel) {
        if (totalItems <= visibleItems) return;

        // 计算滚动条位置和大小
        float scrollPercentage = (float) scrollOffset / (totalItems - visibleItems);
        int scrollBarHeight = Math.max(20, (int) (height * ((float) visibleItems / totalItems)));
        int scrollBarY = y + (int) (scrollPercentage * (height - scrollBarHeight));

        // 滚动条位置（右侧或左侧）
        int scrollBarX = isLeftPanel ? LEFT_PANEL_WIDTH - 6 : this.width - 6;
        int scrollBarWidth = 4;

        // 绘制滚动条背景
        context.fill(scrollBarX, y, scrollBarX + scrollBarWidth, y + height, 0x66444444);

        // 绘制滚动条滑块
        context.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0xCC888888);
    }

    private void renderBottomPanel(DrawContext context, int mouseX, int mouseY) {
        int panelY = this.height - BOTTOM_PANEL_HEIGHT;
        context.fill(0, panelY, this.width, this.height, 0xCC333333);

        int buttonY = panelY + PADDING;
        int centerX = this.width / 2;

        drawCustomButton(context, centerX - 130, buttonY, 80, 20,
                musicPlayer != null && musicPlayer.isPlaying() ? "继续" : "播放", playButtonHovered);
        drawCustomButton(context, centerX - 40, buttonY, 80, 20, "暂停", pauseButtonHovered);
        drawCustomButton(context, centerX + 50, buttonY, 80, 20, "停止", stopButtonHovered);
        drawCustomButton(context, centerX - 180, buttonY, 40, 20, "<<", prevButtonHovered);
        drawCustomButton(context, centerX + 140, buttonY, 40, 20, ">>", nextButtonHovered);

        String modeText = "模式";
        if (musicPlayer != null) {
            switch (musicPlayer.getPlaybackMode()) {
                case LIST_LOOP: modeText = "列表循环"; break;
                case SINGLE_LOOP: modeText = "单曲循环"; break;
                case SHUFFLE: modeText = "随机播放"; break;
            }
        }
        drawCustomButton(context, centerX + 190, buttonY, 80, 20, modeText, modeButtonHovered);
        drawCustomButton(context, centerX + 280, buttonY, 60, 20, "歌词", lyricsButtonHovered);

        int sliderX = PADDING;
        int sliderY = panelY + 40;
        context.drawText(this.textRenderer, "音量:", sliderX, sliderY + 2, 0xFFFFFFFF, false);
        context.fill(sliderX + 35, sliderY, sliderX + 35 + 150, sliderY + 10, 0xFF444444);
        int fillWidth = (int) (150 * volume);
        context.fill(sliderX + 35, sliderY, sliderX + 35 + fillWidth, sliderY + 10, 0xFF4477CC);
        String volumeText = (int) (volume * 100) + "%";
        context.drawText(this.textRenderer, volumeText, sliderX + 35 + 150 + 5, sliderY + 2, 0xFFFFFFFF, false);

        if (musicPlayer != null && musicPlayer.isPlaying()) {
            int progressX = PADDING;
            int progressY = panelY + 60;
            long duration = musicPlayer.getDuration();
            long elapsed = musicPlayer.getElapsedTime();

            context.fill(progressX, progressY, progressX + this.width - PADDING * 2, progressY + 8, 0xFF444444);

            if (duration > 0) {
                int progressWidth = (int) ((this.width - PADDING * 2) * elapsed / duration);
                context.fill(progressX, progressY, progressX + progressWidth, progressY + 8, 0xFF44AA44);
            }

            String timeText = formatTime(elapsed * 1000) + " / " + formatTime(duration * 1000);
            context.drawText(this.textRenderer, timeText, progressX, progressY + 12, 0xFFFFFFFF, false);
        }

        if (musicPlayer != null) {
            String status;
            if (musicPlayer.isPlaying()) {
                MusicData currentSong = musicPlayer.getCurrentMusicData();
                status = (currentSong != null) ? "正在播放: " + currentSong.getTitle() + " - " + currentSong.getArtist() : "正在播放: 未知歌曲";
                context.drawText(this.textRenderer, status, centerX - 200, panelY + 38, 0xFF00FF00, false);

                if (!musicPlayer.getCurrentPlaylist().isEmpty()) {
                    String progressText = "进度: " + (musicPlayer.getCurrentPlaylistIndex() + 1) + "/" + musicPlayer.getCurrentPlaylist().size();
                    context.drawText(this.textRenderer, progressText, centerX + 200, panelY + 38, 0xFFFFFF00, false);
                }
            } else if (musicPlayer.isPaused()) {
                context.drawText(this.textRenderer, "已暂停", centerX - 100, panelY + 38, 0xFFFFFF00, false);
            }
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void drawCustomButton(DrawContext context, int x, int y, int width, int height, String text, boolean hovered) {
        int color = hovered ? 0xFF5555AA : 0xFF444477;
        context.fill(x, y, x + width, y + height, color);
        context.drawBorder(x, y, width, height, 0xFF8888CC);
        context.drawCenteredTextWithShadow(this.textRenderer, text, x + width / 2, y + (height - 8) / 2, 0xFFFFFFFF);
    }

    private void handleSongEnd() {
        if (musicPlayer != null) {
            musicPlayer.playNext();
        }
    }

    // --- 新增鼠标滚轮事件处理 ---
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < LEFT_PANEL_WIDTH && mouseY < this.height - BOTTOM_PANEL_HEIGHT) {
            // 左侧歌单列表滚动
            leftScrollOffset = (int) Math.max(0, Math.min(leftScrollOffset - verticalAmount,
                    Math.max(0, allPlaylists.size() - maxVisibleLeftItems)));
            return true;
        } else if (mouseX > LEFT_PANEL_WIDTH && mouseY < this.height - BOTTOM_PANEL_HEIGHT) {
            // 右侧歌曲列表滚动
            rightScrollOffset = (int) Math.max(0, Math.min(rightScrollOffset - verticalAmount,
                    Math.max(0, currentSongList.size() - maxVisibleRightItems)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // [修复] 将独立的if语句改为if-else if结构，防止点击穿透
        if (mouseX < LEFT_PANEL_WIDTH && mouseY < this.height - BOTTOM_PANEL_HEIGHT) {
            int startY = 25;
            if (mouseY < startY) {
                return true;
            }
            int relativeY = (int) (mouseY - startY);
            int index = leftScrollOffset + (relativeY / ITEM_HEIGHT);

            if (index >= 0 && index < allPlaylists.size()) {
                MusicListData selected = allPlaylists.get(index);
                if (selected.getId() == -1) {
                    loadDailySongs();
                    selectedPlaylistIndex = index;
                    return true;
                } else if (selected.getId() > 0) {
                    selectedPlaylistIndex = index;
                    selectedPlaylistName = selected.getTitle();
                    loadPlaylistSongs(selected.getId());
                    return true;
                }
            }
        } else if (mouseX > LEFT_PANEL_WIDTH && mouseY < this.height - BOTTOM_PANEL_HEIGHT && !currentSongList.isEmpty()) {
            int startY = 30;
            if (mouseY < startY) {
                return true;
            }
            int relativeY = (int) (mouseY - startY);
            int index = rightScrollOffset + (relativeY / ITEM_HEIGHT);

            if (index >= 0 && index < currentSongList.size()) {
                if (musicPlayer != null) {
                    musicPlayer.setPlaylistAndPlay(currentSongList, index);
                }
            }
            return true;
        } else if (mouseY > this.height - BOTTOM_PANEL_HEIGHT) {
            int buttonY = this.height - BOTTOM_PANEL_HEIGHT + PADDING;
            int centerX = this.width / 2;

            if (playButtonHovered) {
                if (musicPlayer != null) {
                    if (musicPlayer.isPaused()) {
                        musicPlayer.webplay();
                    } else if (!currentSongList.isEmpty()) {
                        musicPlayer.setPlaylistAndPlay(currentSongList, 0);
                    } else {
                        MessageUtil.sendMessage("§c请先选择歌单加载歌曲");
                    }
                }
                return true;
            }
            if (pauseButtonHovered) {
                if (musicPlayer != null) musicPlayer.pause();
                return true;
            }
            if (stopButtonHovered) {
                if (musicPlayer != null) musicPlayer.stop();
                return true;
            }
            if (prevButtonHovered) {
                if (musicPlayer != null) musicPlayer.playPrevious();
                return true;
            }
            if (nextButtonHovered) {
                if (musicPlayer != null) musicPlayer.playNext();
                return true;
            }
            if (modeButtonHovered) {
                if (musicPlayer != null) musicPlayer.togglePlaybackMode();
                return true;
            }
            if (lyricsButtonHovered) {
                ovo.baicaijun.laciamusicplayer.gui.LyricRenderer.toggleVisible();
                MessageUtil.sendMessage("§a歌词显示: " +
                        (ovo.baicaijun.laciamusicplayer.gui.LyricRenderer.getVisible() ? "开启" : "关闭"));
                return true;
            }

            int sliderX = PADDING + 35;
            int sliderY = this.height - BOTTOM_PANEL_HEIGHT + 40;
            if (isPointInRect((int) mouseX, (int) mouseY, sliderX, sliderY, 150, 10)) {
                volumeSliderDragging = true;
                updateVolumeFromMouseX((int) mouseX);
                return true;
            }

            if (musicPlayer != null && musicPlayer.isPlaying()) {
                int progressX = PADDING;
                int progressY = this.height - BOTTOM_PANEL_HEIGHT + 60;
                if (isPointInRect((int) mouseX, (int) mouseY, progressX, progressY, this.width - PADDING * 2, 8)) {
                    progressSliderDragging = true;
                    updateProgressFromMouseX((int) mouseX);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    private void updateProgressFromMouseX(int mouseX) {
        if (musicPlayer != null && musicPlayer.isPlaying()) {
            int progressX = PADDING;
            int progressWidth = this.width - PADDING * 2;
            float progress = (float) (mouseX - progressX) / progressWidth;
            progress = Math.max(0, Math.min(1, progress));

            long duration = musicPlayer.getDuration();
            long seekPosition = (long) (duration * progress);
            musicPlayer.seek(seekPosition);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (volumeSliderDragging) {
            updateVolumeFromMouseX((int) mouseX);
            return true;
        }
        if (progressSliderDragging) {
            updateProgressFromMouseX((int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        volumeSliderDragging = false;
        progressSliderDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateButtonHoverState(int mouseX, int mouseY) {
        playButtonHovered = false;
        pauseButtonHovered = false;
        stopButtonHovered = false;
        prevButtonHovered = false;
        nextButtonHovered = false;
        modeButtonHovered = false;
        lyricsButtonHovered = false;

        if (mouseY > this.height - BOTTOM_PANEL_HEIGHT) {
            int panelY = this.height - BOTTOM_PANEL_HEIGHT;
            int buttonY = panelY + PADDING;
            int centerX = this.width / 2;

            playButtonHovered = isPointInRect(mouseX, mouseY, centerX - 130, buttonY, 80, 20);
            pauseButtonHovered = isPointInRect(mouseX, mouseY, centerX - 40, buttonY, 80, 20);
            stopButtonHovered = isPointInRect(mouseX, mouseY, centerX + 50, buttonY, 80, 20);
            prevButtonHovered = isPointInRect(mouseX, mouseY, centerX - 180, buttonY, 40, 20);
            nextButtonHovered = isPointInRect(mouseX, mouseY, centerX + 140, buttonY, 40, 20);
            modeButtonHovered = isPointInRect(mouseX, mouseY, centerX + 190, buttonY, 80, 20);
            lyricsButtonHovered = isPointInRect(mouseX, mouseY, centerX + 280, buttonY, 60, 20);
        }

        if (volumeSliderDragging) {
            updateVolumeFromMouseX(mouseX);
        }
        if (progressSliderDragging) {
            updateProgressFromMouseX(mouseX);
        }
    }

    private void updateVolumeFromMouseX(int mouseX) {
        int sliderX = PADDING + 35;
        volume = (float) (mouseX - sliderX) / 150.0f;
        volume = Math.max(0, Math.min(1, volume));
        if (musicPlayer != null) musicPlayer.setVolume(volume);
    }

    private boolean isPointInRect(int x, int y, int rectX, int rectY, int rectWidth, int rectHeight) {
        return x >= rectX && x <= rectX + rectWidth && y >= rectY && y <= rectY + rectHeight;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
//        if (musicPlayer != null) {
//            musicPlayer.setOnSongEndCallback(null);
//        }
    }
}