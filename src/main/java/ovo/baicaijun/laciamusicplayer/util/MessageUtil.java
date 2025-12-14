package ovo.baicaijun.laciamusicplayer.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MessageUtil {

    public static String format(String message){
        return String.format("§f[§3LaciaMusicPlayer§f] %s",message);
    }

    public static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            // 确保在主线程执行
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.of(format(message)), false);
                }
            });
        }
    }
}
