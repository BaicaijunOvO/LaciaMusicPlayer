package ovo.baicaijun.laciamusicplayer.music;

import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient; // 确保导入了日志工具
import ovo.baicaijun.laciamusicplayer.util.AudioCoverUtil;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author BaicaijunOvO
 * @date 2025/08/25 17:06
 * @modified 修复了因元数据读取失败导致非MP3文件被跳过的问题
 **/
public class MusicManager {
    public static MusicManager instance;
    public static HashMap<String, MusicData> musics = new HashMap<>();
    File musicFolder;

    // --- 核心修复点 1 ---
    // 定义一个支持的音频格式列表，便于管理和检查
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(
            ".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac"
    );

    public static void reload() {
        musics.clear();
        if (MusicManager.instance == null) {
            MusicManager.instance = new MusicManager();
        }
        MusicManager.instance.load();
        MessageUtil.sendMessage("LaciaMusicPlayer reloaded");
    }

    public void load() {
        instance = this;
        musicFolder = new File(MinecraftClient.getInstance().runDirectory, "LaciaMusicPlayer/music");

        if (!musicFolder.exists()) {
            musicFolder.mkdirs();
            return;
        }

        File[] files = musicFolder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String fileName = file.getName().toLowerCase();
            boolean isSupported = SUPPORTED_FORMATS.stream().anyMatch(fileName::endsWith);

            if (isSupported) {
                try {
                    String musicName = file.getName().replaceFirst("[.][^.]+$", "");

                    // [修复] 更健壮的元数据读取
                    String title = AudioCoverUtil.getAudioName(file);
                    String artist = AudioCoverUtil.getAudioArtist(file);
                    String album = AudioCoverUtil.getAudioAlbum(file);
                    long duration = AudioCoverUtil.getAudioDuration(file);

                    // 确保基本信息的有效性
                    if (title == null || title.trim().isEmpty()) {
                        title = musicName; // 使用文件名作为标题
                    }
                    if (artist == null || artist.trim().isEmpty()) {
                        artist = "未知艺术家";
                    }
                    if (album == null || album.trim().isEmpty()) {
                        album = "未知专辑";
                    }

                    musics.put(musicName, new MusicData(
                            title,
                            0,
                            artist,
                            album,
                            musicName,
                            file,
                            null,
                            duration
                    ));

                    LaciamusicplayerClient.LOGGER.info("成功加载: {} - {}", artist, title);

                } catch (Exception e) {
                    // [修复] 更完善的降级处理
                    String musicName = file.getName().replaceFirst("[.][^.]+$", "");

                    musics.put(musicName, new MusicData(
                            musicName,
                            0,
                            "未知艺术家",
                            "未知专辑",
                            musicName,
                            file,
                            null,
                            0
                    ));

                    LaciamusicplayerClient.LOGGER.warn("使用基础信息加载: {}", musicName);
                }
            }
        }
        LaciamusicplayerClient.LOGGER.info("音乐加载完成，共 {} 首曲目", musics.size());
    }
}