package ovo.baicaijun.laciamusicplayer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import ovo.baicaijun.laciamusicplayer.command.CommandManager;
import ovo.baicaijun.laciamusicplayer.config.ConfigManager;
import ovo.baicaijun.laciamusicplayer.gui.LyricRenderer;
import ovo.baicaijun.laciamusicplayer.gui.MusicGUI;
import ovo.baicaijun.laciamusicplayer.gui.NeteaseMusicGUI;
import ovo.baicaijun.laciamusicplayer.music.MusicManager;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;

public class LaciamusicplayerClient implements ClientModInitializer {
    public static MusicPlayer musicPlayer;
    public static CommandManager commandManager;
    public static ConfigManager configManager;
    public static final Logger LOGGER = LogManager.getLogger("LaciaMusicPlayer");
    public static String cookies;
    public static KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("laciamusicplayer"));
    public static KeyBinding guiKeyBinding;
    public static KeyBinding neteaseKeyBinding;
    public static KeyBinding nextKeyBinding;
    public static KeyBinding upKeyBinding;
    public static Thread loginCheckThread;
    public static volatile boolean isLoginChecking = false;

    @Override
    public void onInitializeClient() {
        configManager = new ConfigManager();
        MusicManager musicManager = new MusicManager();
        musicManager.load();
        configManager.load();
        commandManager = new CommandManager();
        musicPlayer = new MusicPlayer();
        commandManager.load();

        neteaseKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.laciamusicplayer.netease",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                CATEGORY  // 使用 KeyBinding.Category 枚举 1.21.10
        ));

        guiKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.laciamusicplayer.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));

        nextKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.laciamusicplayer.next",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                CATEGORY
        ));

        upKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.laciamusicplayer.up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_BRACKET,
                CATEGORY
        ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKeyBinding.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new MusicGUI());
            } else if (nextKeyBinding.wasPressed()) {
                if (MusicPlayer.playMode == 1) musicPlayer.playNext();
                if (MusicPlayer.playMode == 2) MusicGUI.playNext();
            } else if (upKeyBinding.wasPressed()) {
                if (MusicPlayer.playMode == 1) musicPlayer.playPrevious();
                if (MusicPlayer.playMode == 2) MusicGUI.playPrevious();
            } else if (neteaseKeyBinding.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new NeteaseMusicGUI());
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            musicPlayer.stop();
        });


        // 注册歌词渲染
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            LyricRenderer.renderLyrics(drawContext);
        });
    }

    public static void stop() {
        configManager.save();
    }
}