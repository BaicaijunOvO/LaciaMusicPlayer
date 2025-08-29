package ovo.baicaijun.laciamusicplayer.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.Laciamusicplayer;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * MpvUtil: 一个用于加载和交互 libmpv 原生库的工具类。
 * 它封装了所有 JNA 底层调用，为上层提供简洁的接口。
 *
 * @author BaicaijunOvO
 * @date 2025/08/25
 * @modified 修复了致命的内存泄漏问题并优化了初始化逻辑。
 */
public class MpvUtil {

    // --- 接口定义：增加了 mpv_free 和 mpv_set_option_string ---
    private interface MpvLibrary extends Library {
        Pointer mpv_create();
        int mpv_initialize(Pointer mpv);
        void mpv_terminate_destroy(Pointer mpv);
        int mpv_command(Pointer mpv, String[] args);
        int mpv_set_property_string(Pointer mpv, String name, String data);
        int mpv_get_property_string(Pointer mpv, String name, PointerByReference data);
        // --- 核心修复 1：添加释放内存的函数 ---
        void mpv_free(Pointer data);
        // --- 优化点：添加设置选项的函数 ---
        int mpv_set_option_string(Pointer mpv, String name, String data);
    }

    private final MpvLibrary INSTANCE;
    private final Pointer mpvHandle;

    public MpvUtil() {
        try {
            this.INSTANCE = loadMpvLibrary();
            this.mpvHandle = INSTANCE.mpv_create();
            if (mpvHandle == null) {
                throw new RuntimeException("无法创建 mpv 实例");
            }
            // 使用优化后的初始化方法
            initializeMpvOptions();
        } catch (Exception e) {
            MessageUtil.sendMessage("§c初始化 mpv 失败: " + e.getMessage());
            throw new RuntimeException("mpv 初始化失败", e);
        }
    }

    private static MpvLibrary loadMpvLibrary() {
        try {
            File runDirectory = MinecraftClient.getInstance().runDirectory;
            File libDir = new File(runDirectory, "LaciaMusicPlayer/lib");
            File dllFile = new File(libDir, "libmpv.dll");
            if (!libDir.exists()) libDir.mkdirs();

            if (!dllFile.exists()) {
                String resourcePath = "/assets/laciamusicplayer/lib/libmpv.dll";
                try (InputStream dllStream = Laciamusicplayer.class.getResourceAsStream(resourcePath)) {
                    if (dllStream == null) throw new RuntimeException("Mod 资源文件中未找到 libmpv.dll！");
                    Files.copy(dllStream, dllFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.info("自动释放 DLL 失败！请手动将 64位 的 libmpv.dll 放置在以下目录中：" + dllFile.getAbsolutePath());
                    throw new RuntimeException("自动释放 DLL 失败", e);
                }
            }
            return Native.load(dllFile.getAbsolutePath(), MpvLibrary.class);
        } catch (Exception e) {
            throw new RuntimeException("加载 libmpv.dll 失败", e);
        }
    }

    // --- 优化点：使用 mpv_set_option_string 进行初始化 ---
    private void initializeMpvOptions() {
        // 在 mpv_initialize 之前，应该使用 set_option_string
        INSTANCE.mpv_set_option_string(mpvHandle, "video", "no");
        INSTANCE.mpv_set_option_string(mpvHandle, "quiet", "yes");
        // 也可以在这里添加其他选项，例如 `INSTANCE.mpv_set_option_string(mpvHandle, "audio-device", "auto");`

        int result = INSTANCE.mpv_initialize(mpvHandle);
        if (result < 0) {
            throw new RuntimeException("mpv 初始化失败，错误码: " + result);
        }
    }

    public int command(String... args) {
        return INSTANCE.mpv_command(mpvHandle, args);
    }

    public int setProperty(String name, String value) {
        return INSTANCE.mpv_set_property_string(mpvHandle, name, value);
    }

    // --- 核心修复 2：修复 getProperty 中的内存泄漏 ---
    public String getProperty(String name) {
        PointerByReference dataRef = new PointerByReference();
        Pointer dataPtr = null;
        try {
            int result = INSTANCE.mpv_get_property_string(mpvHandle, name, dataRef);
            dataPtr = dataRef.getValue(); // 获取指向原生内存的指针

            if (result >= 0 && dataPtr != null) {
                // 读取Java字符串
                return dataPtr.getString(0);
            }
            return null;
        } finally {
            // 无论成功与否，只要指针非空，就必须释放它！
            if (dataPtr != null) {
                INSTANCE.mpv_free(dataPtr);
            }
        }
    }

    public void destroy() {
        if (mpvHandle != null) {
            INSTANCE.mpv_terminate_destroy(mpvHandle);
        }
    }

    public void setVolume(int volume) {
        setProperty("volume", String.valueOf(volume));
    }
}