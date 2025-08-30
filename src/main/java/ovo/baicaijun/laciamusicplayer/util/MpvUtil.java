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
// --- [最终修复] 导入 Map 和 HashMap ---
import java.util.HashMap;
import java.util.Map;

/**
 * MpvUtil: 一个用于加载和交互 libmpv 原生库的工具类。
 * 它封装了所有 JNA 底层调用，为上层提供简洁的接口。
 *
 * @author BaicaijunOvO
 * @date 2025/08/25
 * @modified [最终修复] 强制JNA使用UTF-8编码，解决中文路径乱码问题。
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
        void mpv_free(Pointer data);
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

            // --- [最终修复] ---
            // 创建一个选项 Map 来指定加载库时的行为。
            Map<String, Object> options = new HashMap<>();
            // 强制 JNA 在与原生库进行字符串转换时使用 UTF-8 编码，解决中文路径乱码问题。
            options.put(Library.OPTION_STRING_ENCODING, "UTF-8");

            // 使用带有选项的 load 方法来加载库。
            return Native.load(dllFile.getAbsolutePath(), MpvLibrary.class, options);
            // --- [修复结束] ---

        } catch (Exception e) {
            throw new RuntimeException("加载 libmpv.dll 失败", e);
        }
    }

    // --- 优化点：使用 mpv_set_option_string 进行初始化 ---
    private void initializeMpvOptions() {
        // [调试] 获取日志文件的路径
        File runDirectory = MinecraftClient.getInstance().runDirectory;
        File logDir = new File(runDirectory, "LaciaMusicPlayer");
        if (!logDir.exists()) logDir.mkdirs();
        File logFile = new File(logDir, "mpv.log");

        // [调试] 启用详细日志记录，并将日志输出到文件
        // --log-file=<路径>  指定日志文件路径，注意需要处理反斜杠
        // -v -v  提高日志详细等级
        INSTANCE.mpv_set_option_string(mpvHandle, "log-file", logFile.getAbsolutePath().replace("\\", "/"));
        INSTANCE.mpv_set_option_string(mpvHandle, "v", ""); // -v
        INSTANCE.mpv_set_option_string(mpvHandle, "v", ""); // -v (两次以获得更详细的信息)
        INSTANCE.mpv_set_option_string(mpvHandle, "log-level", "debug");


        // 在 mpv_initialize 之前，应该使用 set_option_string
        INSTANCE.mpv_set_option_string(mpvHandle, "video", "no");
        INSTANCE.mpv_set_option_string(mpvHandle, "quiet", "yes");

        int result = INSTANCE.mpv_initialize(mpvHandle);
        if (result < 0) {
            throw new RuntimeException("mpv 初始化失败，错误码: " + result);
        }
        LaciamusicplayerClient.LOGGER.info("MPV 日志已启用，输出至: {}", logFile.getAbsolutePath());
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