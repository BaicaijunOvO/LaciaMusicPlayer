package ovo.baicaijun.laciamusicplayer.music.netease;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.music.MusicData;
import ovo.baicaijun.laciamusicplayer.music.MusicListData;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;
import ovo.baicaijun.laciamusicplayer.util.NetworkUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author BaicaijunOvO
 * @date 2025/08/27 23:04
 * @modified 修复异步请求，使用CompletableFuture
 **/
public class NeteaseMusicLoader {
    public static String cookie = LaciamusicplayerClient.cookies;
    public static String baseUrl = "https://163api.qijieya.cn/";
    // 创建后台线程池
    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    public static void testCookie(){
        LaciamusicplayerClient.LOGGER.info(cookie);
    }

    /**
     * 同步获取用户ID
     */
    public static Long getUserID() {
        CompletableFuture<Long> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "user/account", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonObject account = json.get("account").getAsJsonObject();
                            future.complete(account.get("id").getAsLong());
                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取用户ID失败: {}", response);
                            future.complete(0L);
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析用户ID响应失败", e);
                        future.complete(0L);
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取用户ID网络请求失败: {}", error);
                    future.complete(0L);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS); // 10秒超时
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取用户ID超时或失败", e);
            return 0L;
        }
    }

    /**
     * 同步获取推荐歌单列表
     */
    public static List<MusicListData> getRecommendMusicList(){
        CompletableFuture<List<MusicListData>> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "recommend/resource", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        List<MusicListData> musicListData = new ArrayList<>();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonArray playlist = json.get("recommend").getAsJsonArray();
                            playlist.forEach(playlistItem -> {
                                JsonObject playlistItemJson = playlistItem.getAsJsonObject();
                                String title = playlistItemJson.has("name") ?
                                        playlistItemJson.get("name").getAsString() : "未知歌单";
                                String nickname = "网易云音乐";

                                if (playlistItemJson.has("creator")) {
                                    JsonObject creator = playlistItemJson.get("creator").getAsJsonObject();
                                    nickname = creator.has("nickname") ?
                                            creator.get("nickname").getAsString() : nickname;
                                }

                                long id = playlistItemJson.has("id") ?
                                        playlistItemJson.get("id").getAsLong() : 0;

                                musicListData.add(new MusicListData(title, id, nickname));
                            });
                            future.complete(musicListData);
                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取推荐歌单列表失败: {}", response);
                            future.complete(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析推荐歌单列表响应失败", e);
                        future.complete(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取推荐歌单列表网络请求失败: {}", error);
                    future.complete(new ArrayList<>());
                }
            });
        });

        try {
            return future.get(15, TimeUnit.SECONDS); // 15秒超时
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取推荐歌单列表超时或失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 同步获取用户歌单列表
     */
    public static List<MusicListData> getMusicList(long userID) {
        CompletableFuture<List<MusicListData>> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "user/playlist?uid=" + userID + "&limit=30&offset=1", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        List<MusicListData> musicListData = new ArrayList<>();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonArray playlist = json.get("playlist").getAsJsonArray();
                            playlist.forEach(playlistItem -> {
                                JsonObject playlistItemJson = playlistItem.getAsJsonObject();
                                String title = playlistItemJson.has("name") ?
                                        playlistItemJson.get("name").getAsString() : "未知歌单";
                                String nickname = "网易云音乐";

                                if (playlistItemJson.has("creator")) {
                                    JsonObject creator = playlistItemJson.get("creator").getAsJsonObject();
                                    nickname = creator.has("nickname") ?
                                            creator.get("nickname").getAsString() : nickname;
                                }

                                long id = playlistItemJson.has("id") ?
                                        playlistItemJson.get("id").getAsLong() : 0;

                                musicListData.add(new MusicListData(title, id, nickname));
                            });
                            future.complete(musicListData);
                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取用户歌单列表失败: {}", response);
                            future.complete(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析用户歌单列表响应失败", e);
                        future.complete(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取用户歌单列表网络请求失败: {}", error);
                    future.complete(new ArrayList<>());
                }
            });
        });

        try {
            return future.get(15, TimeUnit.SECONDS); // 15秒超时
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取用户歌单列表超时或失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 同步获取音乐URL
     */
    public static String getMusicUrl(long musicID) {
        CompletableFuture<String> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "song/url/v1?id=" + musicID + "&level=exhigh", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonArray dataArray = json.get("data").getAsJsonArray();
                            if (!dataArray.isEmpty()) {
                                JsonObject data = dataArray.get(0).getAsJsonObject();
                                String url = data.has("url") ? data.get("url").getAsString() : null;
                                future.complete(url);
                            } else {
                                future.complete(null);
                            }
                        } else {
                            future.complete(null);
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析音乐URL响应失败", e);
                        future.complete(null);
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取音乐URL网络请求失败: {}", error);
                    future.complete(null);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取音乐URL超时或失败", e);
            return null;
        }
    }

    /**
     * 同步获取音乐时长
     */
    public static long getMusicTime(long musicID) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "song/url/v1?id=" + musicID + "&level=exhigh", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonArray dataArray = json.get("data").getAsJsonArray();
                            if (!dataArray.isEmpty()) {
                                JsonObject data = dataArray.get(0).getAsJsonObject();
                                long time = data.has("time") ? data.get("time").getAsLong() : 0;
                                future.complete(time);
                            } else {
                                future.complete(0L);
                            }
                        } else {
                            future.complete(0L);
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析音乐时长响应失败", e);
                        future.complete(0L);
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取音乐时长网络请求失败: {}", error);
                    future.complete(0L);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取音乐时长超时或失败", e);
            return 0L;
        }
    }

    /**
     * 获取每日推荐歌曲
     */
    public static List<MusicData> getRecommandListMusics(){
        CompletableFuture<List<MusicData>> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "recommend/songs", cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonObject data = json.get("data").getAsJsonObject();
                            JsonArray songsArray = data.get("dailySongs").getAsJsonArray();
                            List<MusicData> musicDataList = new ArrayList<>();

                            // 使用并行处理获取歌曲详情
                            List<CompletableFuture<MusicData>> musicFutures = new ArrayList<>();

                            songsArray.forEach(songItem -> {
                                JsonObject songJson = songItem.getAsJsonObject();
                                long id = songJson.has("id") ? songJson.get("id").getAsLong() : 0;

                                // 处理艺术家信息
                                StringBuilder artistBuilder = new StringBuilder();
                                if (songJson.has("ar")) {
                                    JsonArray artists = songJson.get("ar").getAsJsonArray();
                                    artists.forEach(artistItem -> {
                                        JsonObject artistJson = artistItem.getAsJsonObject();
                                        if (artistJson.has("name")) {
                                            if (artistBuilder.length() > 0) {
                                                artistBuilder.append(" / ");
                                            }
                                            artistBuilder.append(artistJson.get("name").getAsString());
                                        }
                                    });
                                }

                                // 处理专辑信息
                                var lambdaContext = new Object() {
                                    String albumName = "未知专辑";
                                };
                                if (songJson.has("al")) {
                                    JsonObject album = songJson.get("al").getAsJsonObject();
                                    lambdaContext.albumName = album.has("name") ? album.get("name").getAsString() : lambdaContext.albumName;
                                }

                                String title = songJson.has("name") ? songJson.get("name").getAsString() : "未知歌曲";
                                var ref = new Object() {
                                    String artist = artistBuilder.toString();
                                };
                                if (ref.artist.isEmpty()) ref.artist = "未知艺术家";

                                final long finalId = id;
                                // 在后台线程中并行获取URL和时长
                                CompletableFuture<MusicData> musicFuture = CompletableFuture.supplyAsync(() -> {
                                    String url = getMusicUrl(finalId);
                                    long duration = getMusicTime(finalId);
                                    return new MusicData(title, finalId, ref.artist, lambdaContext.albumName, title, null, url, duration);
                                }, backgroundExecutor);

                                musicFutures.add(musicFuture);
                            });

                            // 等待所有歌曲详情获取完成
                            CompletableFuture.allOf(musicFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
                                for (CompletableFuture<MusicData> musicFuture : musicFutures) {
                                    try {
                                        musicDataList.add(musicFuture.get());
                                    } catch (Exception e) {
                                        LaciamusicplayerClient.LOGGER.error("处理音乐数据失败", e);
                                    }
                                }
                                future.complete(musicDataList);
                            });

                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取每日推荐歌曲失败: {}", response);
                            future.complete(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析每日推荐歌曲响应失败", e);
                        future.complete(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取每日推荐歌曲网络请求失败: {}", error);
                    future.complete(new ArrayList<>());
                }
            });
        });

        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            MessageUtil.sendMessage("获取每日推荐歌曲超时或失败 " + e);
            return new ArrayList<>();
        }
    }

    /**
     * 同步获取歌单中的歌曲列表
     */
    public static List<MusicData> getListMusics(long listID) {
        CompletableFuture<List<MusicData>> future = new CompletableFuture<>();
        LaciamusicplayerClient.LOGGER.info("getListMusic ListID: " + listID);

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "playlist/track/all?id=" + listID, cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            JsonArray songsArray = json.get("songs").getAsJsonArray();
                            List<MusicData> musicDataList = new ArrayList<>();

                            // 使用并行处理获取歌曲详情
                            List<CompletableFuture<MusicData>> musicFutures = new ArrayList<>();

                            songsArray.forEach(songItem -> {
                                JsonObject songJson = songItem.getAsJsonObject();
                                long id = songJson.has("id") ? songJson.get("id").getAsLong() : 0;

                                // 处理艺术家信息
                                StringBuilder artistBuilder = new StringBuilder();
                                if (songJson.has("ar")) {
                                    JsonArray artists = songJson.get("ar").getAsJsonArray();
                                    artists.forEach(artistItem -> {
                                        JsonObject artistJson = artistItem.getAsJsonObject();
                                        if (artistJson.has("name")) {
                                            if (artistBuilder.length() > 0) {
                                                artistBuilder.append(" | ");
                                            }
                                            artistBuilder.append(artistJson.get("name").getAsString());
                                        }
                                    });
                                }

                                // 处理专辑信息
                                var lambdaContext = new Object() {
                                    String albumName = "未知专辑";
                                };
                                if (songJson.has("al")) {
                                    JsonObject album = songJson.get("al").getAsJsonObject();
                                    lambdaContext.albumName = album.has("name") ? album.get("name").getAsString() : lambdaContext.albumName;
                                }

                                String title = songJson.has("name") ? songJson.get("name").getAsString() : "未知歌曲";
                                var ref = new Object() {
                                    String artist = artistBuilder.toString();
                                };
                                if (ref.artist.isEmpty()) ref.artist = "未知艺术家";

                                final long finalId = id;
                                // 在后台线程中并行获取URL和时长
                                CompletableFuture<MusicData> musicFuture = CompletableFuture.supplyAsync(() -> {
                                    String url = getMusicUrl(finalId);
                                    long duration = getMusicTime(finalId);
                                    return new MusicData(title, finalId, ref.artist, lambdaContext.albumName, title, null, url, duration);
                                }, backgroundExecutor);

                                musicFutures.add(musicFuture);
                            });

                            // 等待所有歌曲详情获取完成
                            CompletableFuture.allOf(musicFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
                                for (CompletableFuture<MusicData> musicFuture : musicFutures) {
                                    try {
                                        musicDataList.add(musicFuture.get());
                                    } catch (Exception e) {
                                        LaciamusicplayerClient.LOGGER.error("处理音乐数据失败", e);
                                    }
                                }
                                future.complete(musicDataList);
                            });

                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取歌单歌曲失败: {}", response);
                            future.complete(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析歌单歌曲响应失败", e);
                        future.complete(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取歌单歌曲网络请求失败: {}", error);
                    future.complete(new ArrayList<>());
                }
            });
        });

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取歌单歌曲超时或失败", e);
            return new ArrayList<>();
        }
    }

    private static Map<String, String> parseLrcToMap(String lrcText) {
        Map<String, String> lrcMap = new LinkedHashMap<>();
        if (lrcText == null || lrcText.isEmpty()) {
            return lrcMap;
        }

        // 正则表达式，用于匹配 [mm:ss.xx] 或 [mm:ss.xxx] 格式的时间戳
        Pattern pattern = Pattern.compile("(\\[\\d{2}:\\d{2}[.]\\d{2,3}])(.*)");

        for (String line : lrcText.split("\n")) {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches()) {
                String timestamp = matcher.group(1);
                String text = matcher.group(2).trim();
                lrcMap.put(timestamp, text);
            }
        }
        return lrcMap;
    }

    /**
     * 获取歌词
     */
    public static String getLyric(long musicID) {
        CompletableFuture<String> future = new CompletableFuture<>();

        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "lyric?id=" + musicID, cookie, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        String lrc = "";
                        String tlrc = "";

                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

                        // 安全地检查并获取原文歌词
                        if (json.has("lrc") && !json.get("lrc").isJsonNull()) {
                            JsonObject lrcJson = json.get("lrc").getAsJsonObject();
                            if (lrcJson.has("lyric") && !lrcJson.get("lyric").isJsonNull()) {
                                lrc = lrcJson.get("lyric").getAsString();
                            }
                        }

                        // 安全地检查并获取翻译歌词
                        if (json.has("tlyric") && !json.get("tlyric").isJsonNull()) {
                            JsonObject tlyricJson = json.get("tlyric").getAsJsonObject();
                            if (tlyricJson.has("lyric") && !tlyricJson.get("lyric").isJsonNull()) {
                                tlrc = tlyricJson.get("lyric").getAsString();
                            }
                        }

                        // 情况 1: 如果连原文歌词都没有，直接返回"未找到"
                        if (lrc == null || lrc.trim().isEmpty()) {
                            future.complete("[00:00.000]未找到歌词");
                            return;
                        }

                        // 情况 2: 如果只有原文歌词，没有翻译，直接返回原文
                        if (tlrc == null || tlrc.trim().isEmpty()) {
                            future.complete(lrc);
                            return;
                        }

                        // 情况 3: 当原文和译文都存在时，开始逐行合并
                        Map<String, String> lrcMap = parseLrcToMap(lrc);
                        Map<String, String> tlrcMap = parseLrcToMap(tlrc);
                        StringBuilder mergedLyrics = new StringBuilder();

                        for (Map.Entry<String, String> entry : lrcMap.entrySet()) {
                            String timestamp = entry.getKey();
                            String originalLine = entry.getValue();

                            // 根据时间戳查找对应的翻译
                            String translatedLine = tlrcMap.get(timestamp);

                            // 构建新的一行
                            mergedLyrics.append(timestamp);
                            mergedLyrics.append(originalLine);

                            // 如果找到了翻译，则将其附加在原文后
                            if (translatedLine != null && !translatedLine.isEmpty()) {
                                mergedLyrics.append(" | ").append(translatedLine);
                            }

                            mergedLyrics.append("\n");
                        }

                        future.complete(mergedLyrics.toString());

                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析或合并歌词JSON失败, 音乐ID {}", musicID, e);
                        future.complete("[00:00.000]歌词处理失败");
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取歌词网络请求失败: {}", error);
                    future.complete("[00:00.000]网络请求失败");
                }
            });
        });

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取歌曲歌词时发生意外错误, 音乐ID {}", musicID, e);
            return "[00:00.000]获取失败";
        }
    }


    public static void setCookie(String cookie) {
        NeteaseMusicLoader.cookie = cookie;
        LaciamusicplayerClient.LOGGER.info("Cookie: " + NeteaseMusicLoader.cookie);
    }

    public static void reload(){
        setCookie(LaciamusicplayerClient.cookies);
        LaciamusicplayerClient.LOGGER.info("Cookie: " + cookie);
        LaciamusicplayerClient.LOGGER.info("baseUrl: " + baseUrl);
    }
    /**
     * 关闭线程池（可选，在应用退出时调用）
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
    }
}