package ovo.baicaijun.laciamusicplayer.config.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.config.Config;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;
import ovo.baicaijun.laciamusicplayer.music.netease.NeteaseMusicLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * @author BaicaijunOvO
 * @date 2025/08/27 22:35
 **/
public class MusicConfig extends Config {
    public MusicConfig() {
        super("MusicConfig");
    }

    @Override
    public void load() {
        LaciamusicplayerClient.LOGGER.info("Loading MusicConfig..");
        try {
            JsonObject jsonObject = new Gson().fromJson(new String(Files.readAllBytes(getPath()), StandardCharsets.UTF_8), JsonObject.class);
            if (jsonObject.has("volume")) {
                MusicPlayer.volume = jsonObject.get("volume").getAsFloat();
            }
            if (jsonObject.has("cookies")) {
                LaciamusicplayerClient.cookies = jsonObject.get("cookies").getAsString();
                //NeteaseMusicLoader.setCookie(jsonObject.get("cookies").getAsString());
                LaciamusicplayerClient.LOGGER.info("Cookies: " + LaciamusicplayerClient.cookies);
            }
            if (jsonObject.has("baseUrl")) {
                NeteaseMusicLoader.baseUrl = jsonObject.get("baseUrl").getAsString();
                LaciamusicplayerClient.LOGGER.info("baseUrl: " + NeteaseMusicLoader.baseUrl);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save() {

        LaciamusicplayerClient.LOGGER.info("Saving MusicConfig..");
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("volume", MusicPlayer.volume);
        jsonObject.addProperty("cookies", NeteaseMusicLoader.cookie);
        jsonObject.addProperty("baseUrl", NeteaseMusicLoader.baseUrl);
        try {
            Files.write(getPath(), new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
