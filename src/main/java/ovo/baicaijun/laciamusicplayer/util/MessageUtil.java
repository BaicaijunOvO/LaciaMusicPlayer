package ovo.baicaijun.laciamusicplayer.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MessageUtil {

    public static String format(String message){
        return String.format("§f[§3LaciaMusicPlayer§f] %s",message);
    }

    public static void sendMessage(String message){
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.of(format(message)),false);
        }
    }
}
