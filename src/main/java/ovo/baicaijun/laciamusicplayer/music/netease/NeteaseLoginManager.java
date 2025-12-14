package ovo.baicaijun.laciamusicplayer.music.netease;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import net.minecraft.client.MinecraftClient;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.util.CookieUtil;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;
import ovo.baicaijun.laciamusicplayer.util.NetworkUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 网易云音乐登录管理器
 */
public class NeteaseLoginManager {
    private static final int MAX_CHECK_COUNT = 180; // 3分钟
    private static final int CHECK_INTERVAL = 3000; // 3秒，与JavaScript保持一致
    private static File currentQRCodeFile = null;
    public static String baseUrl = "https://api.music.baicaijunovo.xyz/";
    // 创建后台线程池
    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    // 获取 MinecraftClient 实例的辅助方法
    private static MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    /**
     * 在主线程中发送消息
     */
    private static void sendMessageInMainThread(String message) {
        MinecraftClient client = getClient();
        if (client != null) {
            client.execute(() -> MessageUtil.sendMessage(message));
        } else {
            // 客户端可能还未初始化，记录日志
            LaciamusicplayerClient.LOGGER.info("消息无法发送（客户端未就绪）: " + message);
        }
    }

    /**
     * 开始二维码登录流程
     */
    public static void startQRLogin() {
        // 如果已有登录检查在进行中，先停止它
        stopLoginCheck();

        backgroundExecutor.submit(() -> {
            try {
                // 获取unikey
                String unikey = getQrUnikey();
                if (unikey == null || unikey.isEmpty()) {
                    sendMessageInMainThread("§c获取登录密钥失败");
                    return;
                }

                // 获取二维码信息
                JsonObject qrInfo = getQrInfo(unikey);
                if (qrInfo == null) {
                    sendMessageInMainThread("§c获取二维码信息失败");
                    return;
                }

                if (qrInfo.get("code").getAsInt() == 200) {
                    // 获取二维码URL
                    String qrUrl = qrInfo.get("data").getAsJsonObject().get("qrurl").getAsString();

                    // 发送二维码信息给玩家
                    sendMessageInMainThread("§a请使用网易云音乐APP扫描二维码登录");
                    sendMessageInMainThread("§7扫描后请在APP上确认登录");

                    // 生成并显示二维码
                    try {
                        currentQRCodeFile = generateQRCodeImage(qrUrl, 300);
                        openQRCodeImage(currentQRCodeFile);
                        sendMessageInMainThread("§b二维码已生成，正在使用图片查看器打开...");
                        sendMessageInMainThread("§7如果无法自动打开，请手动查看文件: " + currentQRCodeFile.getAbsolutePath());
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.warn("无法生成图片QR码，使用文本回退", e);
                        try {
                            final String asciiQR = generateQRCode(qrUrl, 20, 20);
                            sendMessageInMainThread("§6二维码内容:");
                            sendMessageInMainThread(asciiQR);
                        } catch (WriterException we) {
                            LaciamusicplayerClient.LOGGER.error("生成ASCII二维码失败", we);
                        }
                    }

                    // 开始后台检查
                    startBackgroundCheck(unikey);

                } else {
                    String message = qrInfo.has("message") ? qrInfo.get("message").getAsString() : "未知错误";
                    sendMessageInMainThread("§c获取二维码失败: " + message);
                }
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("登录流程异常", e);
                sendMessageInMainThread("§c登录失败: " + e.getMessage());
            }
        });
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

    private static String getQrUnikey(){
        CompletableFuture<String> future = new CompletableFuture<>();
        backgroundExecutor.submit(() -> {
            NetworkUtil.sendGetRequest(baseUrl + "login/qr/key?timestamp=" + System.currentTimeMillis(), new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            String unikey = json.get("data").getAsJsonObject().get("unikey").getAsString();
                            future.complete(unikey);
                        } else {
                            LaciamusicplayerClient.LOGGER.error("获取Unikey失败: {}", response);
                            future.complete("");
                        }
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析Unikey响应失败", e);
                        future.complete("");
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取Unikey网络请求失败: {}", error);
                    future.complete(null);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取Unikey超时或失败", e);
            return null;
        }
    }

    private static JsonObject getQrInfo(String unikey){
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        backgroundExecutor.submit(() -> {
            // 添加 platform=web 和 qrimg=true 参数，与JavaScript保持一致
            String url = baseUrl + "login/qr/create?key=" + unikey +
                    "&platform=web&qrimg=true&timestamp=" + System.currentTimeMillis();

            NetworkUtil.sendGetRequest(url, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        LaciamusicplayerClient.LOGGER.info("获取二维码信息: " + json);
                        future.complete(json);
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析二维码信息失败", e);
                        future.complete(null);
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("获取二维码信息网络请求失败: {}", error);
                    future.complete(null);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("获取二维码信息超时或失败", e);
            return null;
        }
    }

    private static JsonObject checkLoginStatus(String unikey){
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        backgroundExecutor.submit(() -> {
            // 添加 noCookie=true 参数，与JavaScript保持一致
            String url = baseUrl + "login/qr/check?key=" + unikey +
                    "&noCookie=true&timestamp=" + System.currentTimeMillis();

            NetworkUtil.sendGetRequest(url, new NetworkUtil.NetworkCallback() {
                @Override
                public void onResponse(String response) {
                    try {
                        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                        LaciamusicplayerClient.LOGGER.info("检查登录状态: " + json);
                        future.complete(json);
                    } catch (Exception e) {
                        LaciamusicplayerClient.LOGGER.error("解析登录状态失败", e);
                        future.complete(null);
                    }
                }

                @Override
                public void onFailure(String error) {
                    LaciamusicplayerClient.LOGGER.error("检查登录状态网络请求失败: {}", error);
                    future.complete(null);
                }
            });
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("检查登录状态超时或失败", e);
            return null;
        }
    }

    /**
     * 启动后台扫码状态检查
     */
    private static void startBackgroundCheck(String unikey) {
        LaciamusicplayerClient.isLoginChecking = true;

        LaciamusicplayerClient.loginCheckThread = new Thread(() -> {
            int checkCount = 0;

            try {
                while (checkCount < MAX_CHECK_COUNT &&
                        LaciamusicplayerClient.isLoginChecking &&
                        !Thread.currentThread().isInterrupted()) {

                    JsonObject status = checkLoginStatus(unikey);
                    if (status == null) {
                        sendMessageInMainThread("§c获取登录状态失败，请检查网络连接");
                        break;
                    }

                    int statusCode = status.get("code").getAsInt();

                    switch (statusCode) {
                        case 800: // 二维码过期
                            sendMessageInMainThread("§c二维码已过期，请重新获取");
                            LaciamusicplayerClient.isLoginChecking = false;
                            return;

                        case 801: // 等待扫码
                            if (checkCount % 10 == 0) { // 每30秒提醒一次
                                sendMessageInMainThread("§e等待扫码中...");
                            }
                            break;

                        case 802: // 已扫码，等待确认
                            sendMessageInMainThread("§a✓ 已扫码，请在APP上确认登录");
                            break;

                        case 803: // 登录成功
                            if (status.has("cookie")) {
                                String cookie = status.get("cookie").getAsString();
                                handleLoginSuccess(cookie);
                            } else {
                                sendMessageInMainThread("§c登录成功但无法获取cookie");
                                LaciamusicplayerClient.LOGGER.warn("登录成功但cookie为空: " + status);
                            }
                            LaciamusicplayerClient.isLoginChecking = false;
                            return;

                        default:
                            LaciamusicplayerClient.LOGGER.warn("未知状态码: " + statusCode + ", 响应: " + status);
                            break;
                    }

                    checkCount++;
                    Thread.sleep(CHECK_INTERVAL);
                }

                if (checkCount >= MAX_CHECK_COUNT) {
                    sendMessageInMainThread("§c登录超时（3分钟），请重新获取二维码");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LaciamusicplayerClient.LOGGER.info("登录检查被中断");
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("登录检查错误", e);
                sendMessageInMainThread("§c登录检查失败: " + e.getMessage());
            } finally {
                LaciamusicplayerClient.isLoginChecking = false;
                cleanupTempFiles();
            }
        }, "NeteaseLoginChecker");

        LaciamusicplayerClient.loginCheckThread.setDaemon(true);
        LaciamusicplayerClient.loginCheckThread.start();
    }

    /**
     * 处理登录成功
     */
    private static void handleLoginSuccess(String cookie) {
        try {
            // 解码cookie（处理URL编码）
            String decodedCookie = URLDecoder.decode(cookie, StandardCharsets.UTF_8);

            // 提取和清理cookie
            String essentialCookie = CookieUtil.extractEssentialCookies(decodedCookie);
            essentialCookie = CookieUtil.deduplicateCookies(essentialCookie);

            if (!CookieUtil.isValidCookie(essentialCookie)) {
                sendMessageInMainThread("§c登录失败：cookie格式无效");
                LaciamusicplayerClient.LOGGER.warn("无效的cookie格式: " + essentialCookie);
                return;
            }

            // 保存cookie
            LaciamusicplayerClient.cookies = essentialCookie;
            NeteaseMusicLoader.setCookie(essentialCookie);

            sendMessageInMainThread("§a✓ 登录成功！");
            sendMessageInMainThread("§b正在验证登录状态...");

            // 测试登录状态
            testLoginStatus(essentialCookie);

            LaciamusicplayerClient.LOGGER.info("网易云音乐登录成功，cookie长度: " + essentialCookie.length());

        } catch (Exception e) {
            LaciamusicplayerClient.LOGGER.error("处理登录成功时出错", e);
            sendMessageInMainThread("§c登录处理失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            cleanupTempFiles();
        }
    }

    /**
     * 测试登录状态
     */
    private static void testLoginStatus(String cookie) {
        backgroundExecutor.submit(() -> {
            try {
                String url = baseUrl + "user/account?timestamp=" + System.currentTimeMillis();

                NetworkUtil.sendGetRequest(url, cookie, new NetworkUtil.NetworkCallback() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                            LaciamusicplayerClient.LOGGER.info("登录状态测试响应: " + json);

                            if (json.has("code") && json.get("code").getAsInt() == 200) {
                                JsonObject account = json.get("account").getAsJsonObject();
                                long userId = account.get("id").getAsLong();
                                boolean isAnonymous = account.has("anonimousUser") &&
                                        account.get("anonimousUser").getAsBoolean();

                                if (isAnonymous) {
                                    sendMessageInMainThread("§e警告：当前为匿名用户，部分功能可能受限");
                                    sendMessageInMainThread("§7用户ID: " + userId);
                                } else {
                                    sendMessageInMainThread("§a用户ID: " + userId);
                                    sendMessageInMainThread("§a登录验证成功！");
                                }
                            } else {
                                sendMessageInMainThread("§c登录验证失败，请重新登录");
                            }
                        } catch (Exception e) {
                            LaciamusicplayerClient.LOGGER.error("解析登录测试响应失败", e);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        LaciamusicplayerClient.LOGGER.error("登录测试网络请求失败: {}", error);
                    }
                });
            } catch (Exception e) {
                LaciamusicplayerClient.LOGGER.error("测试登录状态异常", e);
            }
        });
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