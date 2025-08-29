package ovo.baicaijun.laciamusicplayer.config;


import ovo.baicaijun.laciamusicplayer.config.configs.MusicConfig;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final List<Config> configs = new ArrayList<Config>() {
        {
            add(new MusicConfig());
        }
    };

    public void load(){
        for (Config config : configs) {
            if (config.getPath().toFile().exists()) {
                config.load();
            }
        }

    }

    public void save(){

        for (Config config : configs) {

            if (!config.getPath().toFile().exists()) {
                try {
                    Files.createDirectories(config.getPath().getParent());
                    config.getPath().toFile().createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            config.save();
        }

    }

    public void reload(){
        load();
        MessageUtil.sendMessage("Config reloaded");
    }

}
