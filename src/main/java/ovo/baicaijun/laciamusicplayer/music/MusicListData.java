package ovo.baicaijun.laciamusicplayer.music;

/**
 * @author BaicaijunOvO
 * @date 2025/08/27 23:45
 **/
public class MusicListData {
    private String title;
    private long id;
    private String author;
    public MusicListData(String title, long id, String author) {
        this.title = title;
        this.id = id;
        this.author = author;

    }

    public String getTitle() {
        return title;
    }

    public long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }
}
