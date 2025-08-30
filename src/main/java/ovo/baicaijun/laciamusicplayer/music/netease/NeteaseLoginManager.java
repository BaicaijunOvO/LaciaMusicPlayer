package ovo.baicaijun.laciamusicplayer.music.netease;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;
import ovo.baicaijun.laciamusicplayer.util.NeteaseUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 网易云音乐登录管理器
 */
public class NeteaseLoginManager {
    private static final int MAX_CHECK_COUNT = 180; // 3分钟
    private static final int CHECK_INTERVAL = 1000; // 1秒
    private static File currentQRCodeFile = null;

    /**
     * 开始二维码登录流程
     */
    public static void startQRLogin() {
        // 如果已有登录检查在进行中，先停止它
        stopLoginCheck();

        NeteaseUtil netease = new NeteaseUtil();
        if (!netease.isInitialized()) {
            MessageUtil.sendMessage("§c网易云音乐DLL初始化失败");
            return;
        }

        // 清理之前的临时文件
        cleanupTempFiles();

        // 获取二维码
        String qrCodeInfo = netease.getQRCodeInfo();
        LaciamusicplayerClient.LOGGER.info("QR Code Info: " + qrCodeInfo);

        try {
            JsonObject jsonObject = JsonParser.parseString(qrCodeInfo).getAsJsonObject();
            if (jsonObject.get("code").getAsInt() == 200) {
                String qrUrl = jsonObject.get("qrcode_url").getAsString();

                // 发送二维码信息给玩家
                MessageUtil.sendMessage("§a请使用网易云音乐APP扫描二维码登录");

                // 生成并显示QR码
                try {
                    currentQRCodeFile = generateQRCodeImage(qrUrl, 300);
                    openQRCodeImage(currentQRCodeFile);
                    MessageUtil.sendMessage("§b二维码已生成，正在使用图片查看器打开...");
                    MessageUtil.sendMessage("§7如果无法自动打开，请手动查看文件: " + currentQRCodeFile.getAbsolutePath());
                } catch (Exception e) {
                    LaciamusicplayerClient.LOGGER.warn("无法生成图片QR码，使用文本回退", e);
                    // 回退到文本显示
                    String asciiQR = generateQRCode(qrUrl, 20, 20);
                    MessageUtil.sendMessage("§6二维码内容:");
                    MessageUtil.sendMessage(asciiQR);
                }

                // 开始后台检查
                startBackgroundCheck(netease);

            } else {
                String message = jsonObject.get("message").getAsString();
                MessageUtil.sendMessage("§c获取二维码失败: " + message);
            }
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("解析二维码信息失败", e);
            MessageUtil.sendMessage("§c解析二维码信息失败");
        }
    }

    /**
     * 生成QR码图片文件
     */
    private static File generateQRCodeImage(String text, int size) throws WriterException, IOException {
        // 设置QR码参数
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        // 生成BitMatrix
        BitMatrix bitMatrix = new MultiFormatWriter().encode(
                text, BarcodeFormat.QR_CODE, size, size, hints);

        // 转换为BufferedImage
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }

        // 创建临时文件
        Path tempFile = Files.createTempFile("netease_qrcode_", ".png");
        File qrCodeFile = tempFile.toFile();
        ImageIO.write(image, "PNG", qrCodeFile);

        // 程序退出时删除临时文件
        qrCodeFile.deleteOnExit();

        return qrCodeFile;
    }

    /**
     * 使用系统默认程序打开QR码图片
     */
    private static void openQRCodeImage(File imageFile) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(imageFile);
                    return;
                }
            }

            // 备用方案：根据操作系统使用命令行
            String os = System.getProperty("os.name").toLowerCase();
            Runtime runtime = Runtime.getRuntime();

            if (os.contains("win")) {
                runtime.exec("cmd /c start \"\" \"" + imageFile.getAbsolutePath() + "\"");
            } else if (os.contains("mac")) {
                runtime.exec("open \"" + imageFile.getAbsolutePath() + "\"");
            } else if (os.contains("nix") || os.contains("nux")) {
                runtime.exec("xdg-open \"" + imageFile.getAbsolutePath() + "\"");
            }

        } catch (IOException e) {
            LaciamusicplayerClient.LOGGER.warn("无法自动打开图片查看器", e);
        }
    }

    /**
     * 启动后台扫码状态检查
     */
    private static void startBackgroundCheck(NeteaseUtil netease) {
        LaciamusicplayerClient.isLoginChecking = true;

        LaciamusicplayerClient.loginCheckThread = new Thread(() -> {
            int checkCount = 0;

            try {
                while (checkCount < MAX_CHECK_COUNT &&
                        LaciamusicplayerClient.isLoginChecking &&
                        !Thread.currentThread().isInterrupted()) {

                    String status = netease.checkQRStatus();
                    //LaciamusicplayerClient.LOGGER.info("QR Status Response: " + status);

                    JsonObject statusJson = JsonParser.parseString(status).getAsJsonObject();
                    int statusCode = statusJson.get("code").getAsInt();

                    switch (statusCode) {
                        case 800: // 二维码过期
                            MessageUtil.sendMessage("§c二维码已过期，请重新获取");
                            LaciamusicplayerClient.isLoginChecking = false;
                            return;

                        case 801: // 等待扫码
                            if (checkCount % 30 == 0) { // 每30秒提醒一次
                                int minutes = checkCount / 60;
                                int seconds = checkCount % 60;
                                MessageUtil.sendMessage("§e等待扫码中... (" + minutes + "分" + seconds + "秒)");
                            }
                            break;

                        case 802: // 已扫码，等待确认
                            MessageUtil.sendMessage("§a✓ 已扫码，请在APP上确认登录");
                            break;

                        case 803: // 登录成功
                            // 安全地获取cookie字段，可能字段名不同
                            String cookie = getCookieFromResponse(statusJson);
                            if (cookie != null && !cookie.isEmpty()) {
                                handleLoginSuccess(cookie);
                            } else {
                                MessageUtil.sendMessage("§c登录成功但无法获取cookie，请检查API响应");
                                LaciamusicplayerClient.LOGGER.warn("登录成功但cookie为空，响应: " + status);
                            }
                            LaciamusicplayerClient.isLoginChecking = false;
                            return;

                        case 8821: // 风控
                            MessageUtil.sendMessage("§c触发风控限制，请稍后再试");
                            LaciamusicplayerClient.isLoginChecking = false;
                            return;

                        default:
                            LaciamusicplayerClient.LOGGER.warn("未知状态码: " + statusCode + ", 响应: " + status);
                            MessageUtil.sendMessage("§c未知状态码: " + statusCode);
                            break;
                    }

                    checkCount++;
                    Thread.sleep(CHECK_INTERVAL);
                }

                if (checkCount >= MAX_CHECK_COUNT) {
                    MessageUtil.sendMessage("§c登录超时，请重新获取二维码");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LaciamusicplayerClient.LOGGER.info("登录检查被中断");
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("登录检查错误", e);
                MessageUtil.sendMessage("§c登录检查失败: " + e.getMessage());
            } finally {
                LaciamusicplayerClient.isLoginChecking = false;
                cleanupTempFiles();
            }
        }, "NeteaseLoginChecker");

        LaciamusicplayerClient.loginCheckThread.setDaemon(true);
        LaciamusicplayerClient.loginCheckThread.start();
    }

    /**
     * 安全地从响应中获取cookie（处理不同字段名）
     */
    private static String getCookieFromResponse(JsonObject response) {
        // 尝试不同的可能字段名
        if (response.has("cookie") && response.get("cookie").isJsonPrimitive()) {
            return response.get("cookie").getAsString();
        }
        if (response.has("cookies") && response.get("cookies").isJsonPrimitive()) {
            return response.get("cookies").getAsString();
        }
        if (response.has("Cookie") && response.get("Cookie").isJsonPrimitive()) {
            return response.get("Cookie").getAsString();
        }
        if (response.has("Cookies") && response.get("Cookies").isJsonPrimitive()) {
            return response.get("Cookies").getAsString();
        }

        // 如果是对象形式，尝试解析
        if (response.has("cookie") && response.get("cookie").isJsonObject()) {
            JsonObject cookieObj = response.get("cookie").getAsJsonObject();
            if (cookieObj.has("value")) {
                return cookieObj.get("value").getAsString().replace("Cookie: ","");
            }
        }

        LaciamusicplayerClient.LOGGER.warn("无法找到cookie字段，响应: " + response);
        return null;
    }

    /**
     * 处理登录成功
     */
    private static void handleLoginSuccess(String cookie) {
        LaciamusicplayerClient.cookies = cookie;
        NeteaseMusicLoader.setCookie(cookie);
        MessageUtil.sendMessage("§a✓ 登录成功！");
        MessageUtil.sendMessage("§bCookies已保存，可以开始使用网易云音乐功能");

        // 清理临时文件
        cleanupTempFiles();

        LaciamusicplayerClient.LOGGER.info("网易云音乐登录成功");
    }

    /**
     * 清理临时文件
     */
    private static void cleanupTempFiles() {
        if (currentQRCodeFile != null && currentQRCodeFile.exists()) {
            try {
                currentQRCodeFile.delete();
            } catch (SecurityException e) {
                LaciamusicplayerClient.LOGGER.warn("无法删除临时文件: " + currentQRCodeFile.getAbsolutePath(), e);
            }
            currentQRCodeFile = null;
        }
    }

    /**
     * 停止登录检查
     */
    public static void stopLoginCheck() {
        LaciamusicplayerClient.isLoginChecking = false;

        if (LaciamusicplayerClient.loginCheckThread != null &&
                LaciamusicplayerClient.loginCheckThread.isAlive()) {
            LaciamusicplayerClient.loginCheckThread.interrupt();
        }

        // 清理临时文件
        cleanupTempFiles();
    }

    /**
     * 生成ASCII格式的QR码（备用方案）
     */
    public static String generateQRCode(String text, int width, int height) throws WriterException {
        // 设置QR码参数
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        // 生成BitMatrix
        BitMatrix bitMatrix = new MultiFormatWriter().encode(
                text, BarcodeFormat.QR_CODE, width, height, hints);

        // 将BitMatrix转换为ASCII字符
        return convertToAscii(bitMatrix);
    }

    private static String convertToAscii(BitMatrix bitMatrix) {
        StringBuilder sb = new StringBuilder();
        int height = bitMatrix.getHeight();
        int width = bitMatrix.getWidth();

        // 添加顶部边框
        sb.append("┌");
        for (int i = 0; i < width; i++) {
            sb.append("──");
        }
        sb.append("┐\n");

        for (int y = 0; y < height; y++) {
            sb.append("│");
            for (int x = 0; x < width; x++) {
                boolean isBlack = bitMatrix.get(x, y);
                sb.append(isBlack ? "██" : "  ");
            }
            sb.append("│\n");
        }

        // 添加底部边框
        sb.append("└");
        for (int i = 0; i < width; i++) {
            sb.append("──");
        }
        sb.append("┘");

        return sb.toString();
    }

    /**
     * 检查是否正在登录中
     */
    public static boolean isLoggingIn() {
        return LaciamusicplayerClient.isLoginChecking;
    }
}