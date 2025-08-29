package ovo.baicaijun.laciamusicplayer.util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioCoverUtil {

    /**
     * 从音频文件获取封面并注册为纹理
     * @param audioFile 音频文件
     * @return 封面纹理的Identifier，如果获取失败返回null
     */
    public static Identifier getAudioCoverIdentifier(File audioFile) {
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            Tag tag = file.getTag();

            if (tag != null && tag.getFirstArtwork() != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    return registerCoverTexture(artwork);
                }
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to read cover from audio file: {}", audioFile.getName(), e);
        }
        return null;
    }

    /**
     * 将封面图像注册为Minecraft纹理（支持多种图片格式）
     * @param artwork 封面艺术作品
     * @return 注册后的纹理Identifier
     */
    private static Identifier registerCoverTexture(Artwork artwork) {
        try {
            byte[] imageData = artwork.getBinaryData();

            // 方法1：直接尝试使用NativeImage读取（适用于PNG）
            try {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(imageData));
                return createTextureFromNativeImage(nativeImage);
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.debug("NativeImage direct read failed, trying ImageIO conversion");
            }

            // 方法2：使用ImageIO转换格式（适用于JPEG等其他格式）
            try {
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                if (bufferedImage != null) {
                    return convertBufferedImageToTexture(bufferedImage);
                }
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.debug("ImageIO conversion failed", e);
            }

            // 方法3：根据MIME类型处理
            String mimeType = artwork.getMimeType();
            if (mimeType != null) {
                LaciamusicplayerClient.LOGGER.debug("Trying to handle MIME type: {}", mimeType);
                try {
                    BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                    if (bufferedImage != null) {
                        return convertBufferedImageToTexture(bufferedImage);
                    }
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.debug("MIME type specific conversion failed", e);
                }
            }

            LaciamusicplayerClient.LOGGER.warn("All image conversion methods failed for cover art");
            return null;

        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to register cover texture", e);
            return null;
        }
    }

    /**
     * 从NativeImage创建纹理
     */
    private static Identifier createTextureFromNativeImage(NativeImage nativeImage) {
        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            Identifier identifier = Identifier.of("laciamusicplayer", "cover_" + System.currentTimeMillis());
            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
            return identifier;
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to create texture from NativeImage", e);
            return null;
        }
    }

    /**
     * 将BufferedImage转换为Minecraft纹理
     */
    private static Identifier convertBufferedImageToTexture(BufferedImage bufferedImage) {
        try {
            // 将BufferedImage转换为PNG字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", baos);
            byte[] pngData = baos.toByteArray();

            // 使用NativeImage读取PNG数据
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngData));
            return createTextureFromNativeImage(nativeImage);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to convert BufferedImage to texture", e);
            return null;
        }
    }

    /**
     * 获取图片格式信息（用于调试）
     */
    private static String getImageFormatInfo(byte[] imageData) {
        if (imageData.length < 4) return "Too small";

        // PNG signature
        if (imageData[0] == (byte) 0x89 && imageData[1] == 'P' && imageData[2] == 'N' && imageData[3] == 'G') {
            return "PNG";
        }
        // JPEG signature
        else if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
            return "JPEG";
        }
        // GIF signature
        else if (imageData[0] == 'G' && imageData[1] == 'I' && imageData[2] == 'F') {
            return "GIF";
        }
        // BMP signature
        else if (imageData[0] == 'B' && imageData[1] == 'M') {
            return "BMP";
        }
        else {
            return "Unknown (first bytes: " +
                    Integer.toHexString(imageData[0] & 0xFF) + " " +
                    Integer.toHexString(imageData[1] & 0xFF) + " " +
                    Integer.toHexString(imageData[2] & 0xFF) + " " +
                    Integer.toHexString(imageData[3] & 0xFF) + ")";
        }
    }

    /**
     * 从字节数组直接创建封面纹理（带格式检测）
     */
    public static Identifier createCoverTextureFromBytes(byte[] imageData) {
        try {
            LaciamusicplayerClient.LOGGER.debug("Image format: {}", getImageFormatInfo(imageData));

            // 先尝试直接读取
            try {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(imageData));
                return createTextureFromNativeImage(nativeImage);
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.debug("Direct read failed, converting with ImageIO");
            }

            // 使用ImageIO转换
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (bufferedImage != null) {
                return convertBufferedImageToTexture(bufferedImage);
            }

            return null;
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to create cover texture from bytes", e);
            return null;
        }
    }

    /**
     * 清理封面纹理资源
     */
    public static void cleanupCoverTexture(Identifier coverIdentifier) {
        if (coverIdentifier != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(coverIdentifier);
        }
    }

    public static String getAudioName(File file){
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(file);
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            throw new RuntimeException(e);
        }
        return audioFile.getTag().getFirst(FieldKey.TITLE);
    }
    public static String getAudioArtist(File audioFile) {
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            Tag tag = file.getTag();

            if (tag != null) {
                String artist = tag.getFirst(FieldKey.ARTIST);
                if (artist != null && !artist.trim().isEmpty()) {
                    return artist.trim();
                }
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to read artist from audio file: {}", audioFile.getName(), e);
        }
        return "";
    }

    /**
     * 从音频文件获取专辑信息
     */
    public static String getAudioAlbum(File audioFile) {
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            Tag tag = file.getTag();

            if (tag != null) {
                String album = tag.getFirst(FieldKey.ALBUM);
                if (album != null && !album.trim().isEmpty()) {
                    return album.trim();
                }
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to read album from audio file: {}", audioFile.getName(), e);
        }
        return "";
    }
    /**
     * 从音频文件获取时长（毫秒）- 增强版
     */
    public static long getAudioDuration(File audioFile) {
        try {
            AudioFile file = AudioFileIO.read(audioFile);
            AudioHeader header = file.getAudioHeader();

            if (header != null) {
                // 尝试多种方式获取时长
                try {
                    // 方法1: 直接获取轨道长度（秒）
                    int trackLength = header.getTrackLength();
                    if (trackLength > 0) {
                        return trackLength * 1000L;
                    }
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.debug("Method 1 failed for duration", e);
                }

                // 方法2: 通过采样率和帧数计算
                try {
                    int sampleRate = header.getSampleRateAsNumber();
                    int frameCount = Math.toIntExact(header.getNoOfSamples());
                    if (sampleRate > 0 && frameCount > 0) {
                        return (long)(frameCount * 1000.0 / sampleRate);
                    }
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.debug("Method 2 failed for duration", e);
                }

                // 方法3: 通过比特率和文件大小计算（近似）
                try {
                    long fileSize = audioFile.length();
                    int bitRate = Math.toIntExact(header.getBitRateAsNumber());
                    if (bitRate > 0 && fileSize > 0) {
                        return (fileSize * 8000L) / bitRate; // 注意单位转换
                    }
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.debug("Method 3 failed for duration", e);
                }
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.warn("Failed to read duration from audio file: {}", audioFile.getName(), e);
        }

        LaciamusicplayerClient.LOGGER.warn("Could not determine duration for: {}", audioFile.getName());
        return 0;
    }
}