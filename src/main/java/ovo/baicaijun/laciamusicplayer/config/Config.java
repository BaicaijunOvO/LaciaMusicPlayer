package ovo.baicaijun.laciamusicplayer.config;

import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.Laciamusicplayer;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {
    private final String name;


    public Config(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void load(){

    }

    public Path getPath(){
        return Paths.get(MinecraftClient.getInstance().runDirectory.toString(), Laciamusicplayer.MOD_NAME,"config", name + ".json");
    }

    public void save(){

    }


}
