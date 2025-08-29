package ovo.baicaijun.laciamusicplayer.music;

import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.netease.NeteaseMusicLoader;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;
import ovo.baicaijun.laciamusicplayer.util.MpvUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author BaicaijunOvO
 * @date 2025/08/25 18:15
 * @modified 修复暂停/继续逻辑，修复歌曲切换，增加播放模式和自动播放功能
 **/
public class MusicPlayer {
    private File file;
    private String url;

    private long totalDuration; // 总时长（毫秒）
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicLong currentPosition = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final ReentrantLock playbackLock = new ReentrantLock();
    private Thread playbackThread;
    public static float volume = 0.5f;
    private long lastLyricSwitchTime = 0;
    private TreeMap<Long, String> currentLyrics = new TreeMap<>();
    private String currentLyric = "";

    // [修复] 分离本地音乐和网易云音乐的播放列表
    private List<MusicData> localPlaylist = new ArrayList<>(); // 本地音乐播放列表
    private List<MusicData> neteasePlaylist = new ArrayList<>(); // 网易云音乐播放列表
    private int currentLocalIndex = -1; // 本地音乐当前索引
    private int currentNeteaseIndex = -1; // 网易云音乐当前索引
    private PlaybackSource currentSource = PlaybackSource.NONE; // 当前播放源

    private MpvUtil mpvUtil;
    private static boolean libraryLoaded = false;
    public static int playMode = 0;

    // --- 新增功能 ---
    private PlaybackMode playbackMode = PlaybackMode.LIST_LOOP;
    private Runnable onSongEndCallback;

    // [修复] 添加播放源枚举
    private enum PlaybackSource {
        NONE, LOCAL, NETEASE
    }

    public MusicPlayer() {
        if (!libraryLoaded) {
            try {
                this.mpvUtil = new MpvUtil();
                libraryLoaded = true;
                LaciamusicplayerClient.LOGGER.info("MusicPlayer Loaded!");
                setVolume(volume);
                this.onSongEndCallback = this::handleSongEnd;
            } catch (Exception e) {
                throw new RuntimeException("MusicPlayer 初始化失败，无法加载 MPV 引擎。", e);
            }
        }
    }

    public void load(File file, String url, MusicData urlMusicData) {
        playbackLock.lock();
        try {
            // --- 1. 通用重置逻辑 ---
            // 在加载新音频前，彻底停止并清理之前的播放状态
            if (playbackThread != null && playbackThread.isAlive()) {
                playbackThread.interrupt();
            }
            isPlaying.set(false);
            isPaused.set(false);
            currentPosition.set(0);
            this.currentLyrics = null; // 清空上一首歌的歌词
            this.file = null;
            this.url = null;

            // --- 2. 根据是网络还是本地文件进行加载 ---
            if (url != null && file == null) {
                // --- 网络音频加载逻辑 ---
                this.url = url;
                playMode = 2;
                if (urlMusicData != null) {
                    totalDuration = urlMusicData.getDuration();
                    MessageUtil.sendMessage("§7时长: " + formatTime(totalDuration));

                    // [修复] 核心步骤：获取、解析并存储网络歌词
                    String lrcString = NeteaseMusicLoader.getLyric(urlMusicData.getId());
                    this.currentLyrics = LrcParser.parseLrcString(lrcString); // 将解析结果赋值给成员变量

                } else {
                    totalDuration = 0;
                    MessageUtil.sendMessage("§c警告: 无法获取网络音频信息");
                }

            } else if (file != null) {
                // --- 本地文件加载逻辑 ---
                this.file = file;
                playMode = 0;
                String musicName = file.getName().replaceFirst("[.][^.]+$", "");
                MusicData musicData = MusicManager.musics.get(musicName);
                if (musicData != null) {
                    totalDuration = musicData.getDuration();
                    MessageUtil.sendMessage("§7时长: " + formatTime(totalDuration));
                } else {
                    totalDuration = 0;
                    MessageUtil.sendMessage("§c警告: 无法获取本地音频时长信息");
                }

                // 加载本地歌词文件
                loadLyrics(file);
                MessageUtil.sendMessage("§a本地音频文件加载完成，准备播放");
            }

        } finally {
            playbackLock.unlock();
        }
    }

    public void webplay() {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (isPlaying.get()) {
            return; // 如果已在播放，则不执行任何操作
        }

        playbackLock.lock();
        try {
            if (isPaused.get()) { // --- 这是继续播放逻辑 ---
                isPaused.set(false);
                isPlaying.set(true);
                startTime.set(System.currentTimeMillis() - currentPosition.get()); // 重新计算开始时间
                mpvUtil.command("set", "pause", "no");
                MessageUtil.sendMessage("§a继续播放...");
                startPlaybackTimer();
            } else { // --- 这是从头开始播放逻辑 ---
                isPlaying.set(true);
                isPaused.set(false);
                currentPosition.set(0);
                startTime.set(System.currentTimeMillis());

                int result = mpvUtil.command("loadfile", url, "replace");
                if (result < 0) {
                    MessageUtil.sendMessage("§c播放命令失败: " + result);
                    isPlaying.set(false);
                    return;
                }

                MessageUtil.sendMessage("§a开始播放音频...");
                setVolume(volume);
                updateCurrentLyric();
                startPlaybackTimer();
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 播放当前加载的音频。如果已暂停，则继续播放。
     */
    public void play() {
        if (file == null) {
            MessageUtil.sendMessage("§c请先加载音频文件！");
            return;
        }
        if (isPlaying.get()) {
            return; // 如果已在播放，则不执行任何操作
        }

        playbackLock.lock();
        try {
            if (isPaused.get()) { // --- 这是继续播放逻辑 ---
                isPaused.set(false);
                isPlaying.set(true);
                startTime.set(System.currentTimeMillis() - currentPosition.get()); // 重新计算开始时间
                mpvUtil.command("set", "pause", "no");
                MessageUtil.sendMessage("§a继续播放...");
                startPlaybackTimer();
            } else { // --- 这是从头开始播放逻辑 ---
                isPlaying.set(true);
                isPaused.set(false);
                currentPosition.set(0);
                startTime.set(System.currentTimeMillis());

                int result = mpvUtil.command("loadfile", file.getAbsolutePath(), "replace");
                if (result < 0) {
                    MessageUtil.sendMessage("§c播放命令失败: " + result);
                    isPlaying.set(false);
                    return;
                }

                MessageUtil.sendMessage("§a开始播放音频...");
                setVolume(volume);
                updateCurrentLyric();
                startPlaybackTimer();
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 如果正在播放，则暂停。
     */
    public void pause() {
        playbackLock.lock();
        try {
            if (isPlaying.get()) { // 只能在播放时暂停
                isPlaying.set(false); // 这会使计时器线程停止
                isPaused.set(true);
                mpvUtil.command("set", "pause", "yes");
                MessageUtil.sendMessage("§6播放已暂停");
            }
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 完全停止播放。
     */
    public void stop() {
        playbackLock.lock();
        try {
            if (!isPlaying.get() && !isPaused.get()) return; // 如果已经停止了，就不用再停了

            isPlaying.set(false);
            isPaused.set(false);
            currentPosition.set(0);
            if (playbackThread != null) {
                playbackThread.interrupt();
            }

            mpvUtil.command("stop");
            MessageUtil.sendMessage("§6停止播放音频");

            currentLyric = "";
            ovo.baicaijun.laciamusicplayer.gui.LyricRenderer.reset();
        } finally {
            playbackLock.unlock();
        }
    }

    private void startPlaybackTimer() {
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }

        playbackThread = new Thread(() -> {
            boolean completedNaturally = false;
            try {
                while (isPlaying.get()) {
                    long elapsed = System.currentTimeMillis() - startTime.get();
                    currentPosition.set(elapsed);
                    updateCurrentLyric();

                    if (totalDuration > 0 && elapsed >= totalDuration) {
                        completedNaturally = true;
                        break;
                    }

                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                LaciamusicplayerClient.LOGGER.debug("Playback timer interrupted.");
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("Playback timer error", e);
            } finally {
                // 当循环结束（无论是暂停、停止还是播放完成），确保 isPlaying 为 false
                isPlaying.set(false);

                if (completedNaturally) {
                    // 自然播放完成，需要停止MPV播放器
                    mpvUtil.command("stop");
                    currentPosition.set(totalDuration); // 设置到结束位置
                    updateCurrentLyric();

                    MessageUtil.sendMessage("§a音频播放完成！");

                    // [修复] 无论GUI是否打开，都会执行回调
                    if (onSongEndCallback != null) {
                        MinecraftClient.getInstance().execute(onSongEndCallback);
                    }
                }
            }
        }, "PlaybackTimer");

        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    // --- 新增和修改的 getter/setter 和控制方法 ---

    public void setOnSongEndCallback(Runnable onSongEndCallback) {
        if (onSongEndCallback != null) {
            this.onSongEndCallback = onSongEndCallback;
        } else {
            // 如果尝试设置为null，恢复默认行为
            this.onSongEndCallback = this::handleSongEnd;
        }
    }

    public PlaybackMode getPlaybackMode() {
        return this.playbackMode;
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public PlaybackMode togglePlaybackMode() {
        switch (playbackMode) {
            case LIST_LOOP:
                playbackMode = PlaybackMode.SINGLE_LOOP;
                MessageUtil.sendMessage("§b播放模式: §a单曲循环");
                break;
            case SINGLE_LOOP:
                playbackMode = PlaybackMode.SHUFFLE;
                MessageUtil.sendMessage("§b播放模式: §a随机播放");
                break;
            case SHUFFLE:
                playbackMode = PlaybackMode.LIST_LOOP;
                MessageUtil.sendMessage("§b播放模式: §a列表循环");
                break;
        }
        return playbackMode;
    }

    // --- 其他方法保持不变 ---
    public void loadLyrics(File audioFile) {
        if (currentLyrics != null) {
            currentLyrics.clear();
        }
        currentLyric = "";
        ovo.baicaijun.laciamusicplayer.gui.LyricRenderer.reset();
        String baseName = audioFile.getName().replaceFirst("[.][^.]+$", "");
        File lrcFile = new File(audioFile.getParent(), baseName + ".lrc");
        if (!lrcFile.exists() || !lrcFile.isFile()) {
            LaciamusicplayerClient.LOGGER.info("No LRC file found for: {}", audioFile.getName());
            MessageUtil.sendMessage("§7未找到匹配的歌词文件。");
            return;
        }
        try {
            TreeMap<Long, String> parsedLyrics = LrcParser.parseLrcFile(lrcFile);
            if (parsedLyrics != null && !parsedLyrics.isEmpty()) {
                currentLyrics = parsedLyrics;
                MessageUtil.sendMessage("§a成功加载 " + currentLyrics.size() + " 行歌词。");
                LaciamusicplayerClient.LOGGER.info("Successfully loaded {} lyrics from {}", currentLyrics.size(), lrcFile.getName());
            } else {
                MessageUtil.sendMessage("§e警告: 歌词文件为空或格式不正确。");
                LaciamusicplayerClient.LOGGER.warn("LRC file was found but no lyrics were parsed: {}", lrcFile.getName());
            }
        } catch (Exception e) {
            currentLyrics.clear();
            MessageUtil.sendMessage("§c加载歌词时发生未知错误！");
            LaciamusicplayerClient.LOGGER.error("An unexpected error occurred while loading lyrics from {}", lrcFile.getName(), e);
        }
    }

    private void updateCurrentLyric() {
        if (currentLyrics != null && !currentLyrics.isEmpty() && currentPosition.get() >= 0) {
            String newLyric = LrcParser.getCurrentLyric(
                    currentLyrics, currentPosition.get());
            if (newLyric != null && !newLyric.equals(currentLyric)) {
                long switchTime = System.currentTimeMillis();
                LaciamusicplayerClient.LOGGER.debug("Lyric switched: '{}' -> '{}' at {}ms (elapsed: {}ms)",
                        currentLyric, newLyric, currentPosition.get(), switchTime - lastLyricSwitchTime);
                currentLyric = newLyric;
                lastLyricSwitchTime = switchTime;
                ovo.baicaijun.laciamusicplayer.gui.LyricRenderer.onLyricChanged(newLyric, currentPosition.get());
            }
        }
    }
    public String getCurrentLyric() { return currentLyric; }
    public boolean hasLyrics() { return currentLyrics != null && !currentLyrics.isEmpty(); }
    public void seek(double seconds) {
        playbackLock.lock();
        try {
            long seekPos = (long) (seconds * 1000);
            currentPosition.set(seekPos);
            if (isPlaying.get() || isPaused.get()) {
                startTime.set(System.currentTimeMillis() - seekPos);
                mpvUtil.command("seek", String.valueOf(seconds), "absolute");
            }
            updateCurrentLyric();
        } finally {
            playbackLock.unlock();
        }
    }
    public void setVolume(float volume) {
        MusicPlayer.volume = Math.max(0.0f, Math.min(1.0f, volume));
        int intVolume = (int) (MusicPlayer.volume * 100);
        setVolume(intVolume);
    }
    public float getVolume() { return volume; }
    public void setVolume(int volume) {
        int clampedVolume = Math.min(Math.max(volume, 0), 130);
        mpvUtil.setProperty("volume", String.valueOf(clampedVolume));
    }
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    public boolean isPlaying() { return isPlaying.get(); }
    public String getCurrentFile() { return file != null ? file.getName() : "无文件"; }
    public int getPlaybackProgress() {
        return totalDuration == 0 ? 0 : (int) ((currentPosition.get() * 100) / totalDuration);
    }
    public long getElapsedTime() { return currentPosition.get() / 1000; }
    public long getDuration() { return totalDuration / 1000; }
    public void close() {
        stop();
        if (mpvUtil != null) {
            mpvUtil.destroy();
            libraryLoaded = false;
        }
        MessageUtil.sendMessage("§7播放器资源已释放");
    }

    /**
     * 设置播放列表并开始播放指定位置的歌曲
     */
    public void setPlaylistAndPlay(List<MusicData> playlist, int index) {
        playbackLock.lock();
        try {
            // [修复] 根据播放列表类型设置不同的源
            if (playlist != null && !playlist.isEmpty()) {
                MusicData firstSong = playlist.get(0);
                if (firstSong.getUrl() != null && !firstSong.getUrl().isEmpty()) {
                    // 网易云音乐播放列表
                    this.neteasePlaylist = new ArrayList<>(playlist);
                    this.currentNeteaseIndex = index;
                    this.currentSource = PlaybackSource.NETEASE;
                } else {
                    // 本地音乐播放列表
                    this.localPlaylist = new ArrayList<>(playlist);
                    this.currentLocalIndex = index;
                    this.currentSource = PlaybackSource.LOCAL;
                }
            }

            MusicData song = playlist.get(index);
            stop(); // 先停止当前播放

            if (song.getUrl() != null && !song.getUrl().isEmpty()) {
                // 播放网易云音乐
                load(null, song.getUrl(), song);
                webplay();
            } else {
                // 播放本地音乐
                String musicName = song.getTitle();
                MusicData musicData = MusicManager.musics.get(musicName);
                if (musicData != null && musicData.getFile() != null) {
                    load(musicData.getFile(), null, null);
                    play();
                } else {
                    MessageUtil.sendMessage("§c无法找到本地音乐文件: " + musicName);
                    return;
                }
            }

            // 发送播放通知
            MessageUtil.sendMessage("§a正在播放: §e" + song.getTitle() + " §7- §b" + song.getArtist());
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 获取当前正在播放的音乐数据
     */
    public MusicData getCurrentMusicData() {
        switch (currentSource) {
            case LOCAL:
                if (currentLocalIndex >= 0 && currentLocalIndex < localPlaylist.size()) {
                    return localPlaylist.get(currentLocalIndex);
                }
                break;
            case NETEASE:
                if (currentNeteaseIndex >= 0 && currentNeteaseIndex < neteasePlaylist.size()) {
                    return neteasePlaylist.get(currentNeteaseIndex);
                }
                break;
            case NONE:
            default:
                break;
        }
        return null;
    }

    /**
     * 播放下一首
     */
    public void playNext() {
        playbackLock.lock();
        try {
            List<MusicData> currentPlaylist;
            int currentIndex;

            // [修复] 根据当前播放源选择正确的播放列表
            switch (currentSource) {
                case LOCAL:
                    if (localPlaylist.isEmpty()) {
                        MessageUtil.sendMessage("§c本地播放列表为空");
                        return;
                    }
                    currentPlaylist = localPlaylist;
                    currentIndex = currentLocalIndex;
                    break;
                case NETEASE:
                    if (neteasePlaylist.isEmpty()) {
                        MessageUtil.sendMessage("§c网易云播放列表为空");
                        return;
                    }
                    currentPlaylist = neteasePlaylist;
                    currentIndex = currentNeteaseIndex;
                    break;
                case NONE:
                default:
                    MessageUtil.sendMessage("§c没有正在播放的列表");
                    return;
            }

            int nextIndex;
            switch (playbackMode) {
                case LIST_LOOP:
                    nextIndex = (currentIndex + 1) % currentPlaylist.size();
                    break;
                case SINGLE_LOOP:
                    nextIndex = currentIndex; // 单曲循环，播放同一首
                    break;
                case SHUFFLE:
                    nextIndex = new Random().nextInt(currentPlaylist.size());
                    break;
                default:
                    nextIndex = (currentIndex + 1) % currentPlaylist.size();
            }

            playSongAtIndex(nextIndex);
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 播放上一首
     */
    public void playPrevious() {
        playbackLock.lock();
        try {
            List<MusicData> currentPlaylist;
            int currentIndex;

            // [修复] 根据当前播放源选择正确的播放列表
            switch (currentSource) {
                case LOCAL:
                    if (localPlaylist.isEmpty()) {
                        MessageUtil.sendMessage("§c本地播放列表为空");
                        return;
                    }
                    currentPlaylist = localPlaylist;
                    currentIndex = currentLocalIndex;
                    break;
                case NETEASE:
                    if (neteasePlaylist.isEmpty()) {
                        MessageUtil.sendMessage("§c网易云播放列表为空");
                        return;
                    }
                    currentPlaylist = neteasePlaylist;
                    currentIndex = currentNeteaseIndex;
                    break;
                case NONE:
                default:
                    MessageUtil.sendMessage("§c没有正在播放的列表");
                    return;
            }

            int prevIndex;
            switch (playbackMode) {
                case LIST_LOOP:
                    prevIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
                    break;
                case SINGLE_LOOP:
                    prevIndex = currentIndex; // 单曲循环，播放同一首
                    break;
                case SHUFFLE:
                    prevIndex = new Random().nextInt(currentPlaylist.size());
                    break;
                default:
                    prevIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
            }

            playSongAtIndex(prevIndex);
        } finally {
            playbackLock.unlock();
        }
    }

    /**
     * 播放指定索引的歌曲
     */
    private void playSongAtIndex(int index) {
        List<MusicData> currentPlaylist;

        // [修复] 根据当前播放源选择正确的播放列表
        switch (currentSource) {
            case LOCAL:
                currentPlaylist = localPlaylist;
                break;
            case NETEASE:
                currentPlaylist = neteasePlaylist;
                break;
            case NONE:
            default:
                MessageUtil.sendMessage("§c没有正在播放的列表");
                return;
        }

        if (index < 0 || index >= currentPlaylist.size()) {
            MessageUtil.sendMessage("§c无效的歌曲索引");
            return;
        }

        // [修复] 更新正确的索引
        if (currentSource == PlaybackSource.LOCAL) {
            currentLocalIndex = index;
        } else {
            currentNeteaseIndex = index;
        }

        MusicData song = currentPlaylist.get(index);

        stop();

        if (song.getUrl() != null && !song.getUrl().isEmpty()) {
            // 播放网易云音乐
            load(null, song.getUrl(), song);
            webplay();
        } else {
            // 播放本地音乐
            String musicName = song.getTitle();
            MusicData musicData = MusicManager.musics.get(musicName);
            if (musicData != null && musicData.getFile() != null) {
                load(musicData.getFile(), null, null);
                play();
            } else {
                MessageUtil.sendMessage("§c无法找到本地音乐文件: " + musicName);
                return;
            }
        }

        // 发送播放通知
        MessageUtil.sendMessage("§a正在播放: §e" + song.getTitle() + " §7- §b" + song.getArtist());
    }

    /**
     * 获取当前播放列表
     */
    public List<MusicData> getCurrentPlaylist() {
        switch (currentSource) {
            case LOCAL:
                return new ArrayList<>(localPlaylist);
            case NETEASE:
                return new ArrayList<>(neteasePlaylist);
            case NONE:
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 获取当前播放索引
     */
    public int getCurrentPlaylistIndex() {
        switch (currentSource) {
            case LOCAL:
                return currentLocalIndex;
            case NETEASE:
                return currentNeteaseIndex;
            case NONE:
            default:
                return -1;
        }
    }

    private void handleSongEnd() {
        // 根据播放模式自动播放下一首
        if (currentSource != PlaybackSource.NONE) {
            MinecraftClient.getInstance().execute(() -> {
                switch (playbackMode) {
                    case LIST_LOOP:
                    case SHUFFLE:
                        playNext();
                        break;
                    case SINGLE_LOOP:
                        // 单曲循环，重新播放当前歌曲
                        playSongAtIndex(getCurrentPlaylistIndex());
                        break;
                }
            });
        }
    }

    public void debugLyrics() { /* ... */ }
}