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
                continue; // 只处理文件
            }

            String fileName = file.getName().toLowerCase();

            // 使用新的列表来检查文件格式是否受支持
            boolean isSupported = SUPPORTED_FORMATS.stream().anyMatch(fileName::endsWith);

            if (isSupported) {
                // --- 核心修复点 2: 添加 try-catch 块 ---
                try {
                    // 尝试读取完整的元数据
                    String musicName = file.getName().replaceFirst("[.][^.]+$", ""); // 更稳健的去扩展名方式

                    musics.put(musicName, new MusicData(
                            AudioCoverUtil.getAudioName(file),
                            0,
                            AudioCoverUtil.getAudioArtist(file),
                            AudioCoverUtil.getAudioAlbum(file),
                            musicName,
                            file,
                            null,
                            AudioCoverUtil.getAudioDuration(file)
                    ));
                    LaciamusicplayerClient.LOGGER.info("Successfully loaded music with metadata: {}", file.getName());

                } catch (Exception e) {
                    // 如果读取元数据失败，则进行“优雅降级”，只加载基础信息
                    LaciamusicplayerClient.LOGGER.error("Failed to load metadata for '{}'. Adding with basic info.", file.getName(), e);

                    String musicName = file.getName().replaceFirst("[.][^.]+$", "");

                    // 使用文件名作为标题，其他信息设为默认值
                    musics.put(musicName, new MusicData(
                            musicName,
                            0,
                            "未知艺术家",
                            "未知专辑",
                            musicName,
                            file,
                            null,
                            0 // 时长未知
                    ));
                }
            }
        }
        LaciamusicplayerClient.LOGGER.info("Music discovery complete. Total tracks loaded: {}", musics.size());
    }
}