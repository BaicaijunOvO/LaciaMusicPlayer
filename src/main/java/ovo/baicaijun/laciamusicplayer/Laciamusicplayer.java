package ovo.baicaijun.laciamusicplayer;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.gui.DrawContext;

public class Laciamusicplayer implements ModInitializer {

    public static DrawContext context;
    public static String MOD_ID = "laciamusicplayer";
    public static String MOD_NAME = "LaciaMusicPlayer";


    @Override
    public void onInitialize() {
    }

    public static void setContext(DrawContext context) {
        Laciamusicplayer.context = context;
    }
}
