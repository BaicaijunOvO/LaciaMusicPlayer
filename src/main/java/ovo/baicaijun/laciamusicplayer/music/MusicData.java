package ovo.baicaijun.laciamusicplayer.music;

import net.minecraft.util.Identifier;
import java.io.File;

/**
 * @author BaicaijunOvO
 * @date 2025/08/26 21:00
 **/
public class MusicData {
    long id;
    String title;
    String artist;
    String album;
    String realName;
    File file;
    Identifier identifier;
    long duration; // 存储音频时长（毫秒）
    String url;

    /**
     * 储存歌曲信息
     */
// 修改 MusicData 的在线音乐构造函数
    public MusicData(String title, long id,String artist, String album,String realName,File file, String url, long duration) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.realName = realName;
        this.url = url;
        this.duration = duration;
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getRealName() {
        return realName;
    }

    public long getDuration() {
        return duration;
    }

    public String getUrl() {
        return url;
    }

    public long getId() {
        return id;
    }
}