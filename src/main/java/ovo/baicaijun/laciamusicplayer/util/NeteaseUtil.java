package ovo.baicaijun.laciamusicplayer.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.Laciamusicplayer;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * NeteaseUtil: 网易云音乐二维码登录工具类
 * 直接调用DLL函数获取二维码和检查状态，无需HTTP服务器
 *
 * @author BaicaijunOvO
 * @date 2025/08/29
 */
public class NeteaseUtil {

    // --- 网易云音乐DLL接口定义 ---
    private interface NeteaseLibrary extends Library {
        // 释放字符串内存
        void FreeString(Pointer str);

        // 获取二维码信息
        Pointer GetQRCodeInfo();

        // 检查扫码状态
        Pointer CheckQRStatus();
    }

    private final NeteaseLibrary INSTANCE;
    private boolean isInitialized = false;

    public NeteaseUtil() {
        try {
            this.INSTANCE = loadNeteaseLibrary();
            this.isInitialized = true;
            LaciamusicplayerClient.LOGGER.info("网易云音乐DLL初始化成功");
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("初始化网易云音乐DLL失败: " + e.getMessage());
            throw new RuntimeException("网易云音乐DLL初始化失败", e);
        }
    }

    private static NeteaseLibrary loadNeteaseLibrary() {
        try {
            File runDirectory = MinecraftClient.getInstance().runDirectory;
            File libDir = new File(runDirectory, "LaciaMusicPlayer/lib");
            File dllFile = new File(libDir, "netease.dll");

            if (!libDir.exists()) {
                libDir.mkdirs();
            }

            // 检查DLL文件是否存在，如果不存在则从资源中释放
            if (!dllFile.exists()) {
                String resourcePath = "/assets/laciamusicplayer/lib/netease.dll";
                try (InputStream dllStream = Laciamusicplayer.class.getResourceAsStream(resourcePath)) {
                    if (dllStream == null) {
                        throw new RuntimeException("Mod资源文件中未找到netease.dll！");
                    }
                    Files.copy(dllStream, dllFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LaciamusicplayerClient.LOGGER.info("已自动释放netease.dll");
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.error("自动释放netease.dll失败！请手动将netease.dll放置在以下目录中：" + dllFile.getAbsolutePath());
                    throw new RuntimeException("自动释放netease.dll失败", e);
                }
            }

            return Native.load(dllFile.getAbsolutePath(), NeteaseLibrary.class);
        } catch (Exception e) {
            throw new RuntimeException("加载netease.dll失败", e);
        }
    }

    /**
     * 获取二维码登录信息
     * @return JSON格式的二维码信息
     */
    public String getQRCodeInfo() {
        if (!isInitialized) {
            return "{\"code\":500,\"message\":\"DLL未初始化\"}";
        }

        Pointer result = INSTANCE.GetQRCodeInfo();
        try {
            return result != null ? result.getString(0) : "{\"code\":500,\"message\":\"获取二维码失败\"}";
        } finally {
            if (result != null) {
                INSTANCE.FreeString(result);
            }
        }
    }

    /**
     * 检查二维码扫码状态
     * @return JSON格式的扫码状态信息
     */
    public String checkQRStatus() {
        if (!isInitialized) {
            return "{\"code\":500,\"message\":\"DLL未初始化\"}";
        }

        Pointer result = INSTANCE.CheckQRStatus();
        try {
            return result != null ? result.getString(0) : "{\"code\":500,\"message\":\"检查状态失败\"}";
        } finally {
            if (result != null) {
                INSTANCE.FreeString(result);
            }
        }
    }

    /**
     * 检查DLL是否初始化成功
     * @return true如果初始化成功
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 清理资源
     */
    public void destroy() {
        // DLL没有需要手动销毁的资源
    }

    /**
     * 解析JSON响应中的code字段
     * @param jsonResponse JSON响应字符串
     * @return 状态码，如果解析失败返回-1
     */
    public int parseResponseCode(String jsonResponse) {
        try {
            // 简单的JSON解析，只提取code字段
            int codeIndex = jsonResponse.indexOf("\"code\":");
            if (codeIndex == -1) return -1;

            int start = codeIndex + 7; // "code": 的长度
            int end = jsonResponse.indexOf(",", start);
            if (end == -1) end = jsonResponse.indexOf("}", start);
            if (end == -1) return -1;

            String codeStr = jsonResponse.substring(start, end).trim();
            return Integer.parseInt(codeStr);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 解析JSON响应中的message字段
     * @param jsonResponse JSON响应字符串
     * @return 消息内容，如果解析失败返回空字符串
     */
    public String parseResponseMessage(String jsonResponse) {
        try {
            int messageIndex = jsonResponse.indexOf("\"message\":\"");
            if (messageIndex == -1) return "";

            int start = messageIndex + 11; // "message":" 的长度
            int end = jsonResponse.indexOf("\"", start);
            if (end == -1) return "";

            return jsonResponse.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 检查是否扫码成功
     * @return true如果扫码成功并包含cookies
     */
    public boolean isLoginSuccessful() {
        String status = checkQRStatus();
        return parseResponseCode(status) == 803;
    }

    /**
     * 获取登录成功的cookies
     * @return cookies字符串，如果未登录成功返回空字符串
     */
    public String getLoginCookies() {
        String status = checkQRStatus();
        if (parseResponseCode(status) != 803) {
            return "";
        }

        try {
            int cookiesIndex = status.indexOf("\"cookies\":\"");
            if (cookiesIndex == -1) return "";

            int start = cookiesIndex + 11; // "cookies":" 的长度
            int end = status.indexOf("\"", start);
            if (end == -1) return "";

            return status.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }
}