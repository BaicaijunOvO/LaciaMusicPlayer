package ovo.baicaijun.laciamusicplayer.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkUtil {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // 异步GET请求（不带cookie）
    public static void sendGetRequest(String urlString, final NetworkCallback callback) {
        executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        // 添加允许的请求头
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        // 注意：Java HttpClient 不允许设置 Connection 头
                        // .header("Connection", "keep-alive") // 移除这个
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(response, callback);
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    // 异步GET请求（带cookie）
    public static void sendGetRequest(String urlString, String cookie, final NetworkCallback callback) {
        executor.submit(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        // 添加允许的请求头
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", "https://music.163.com/")
                        .header("Origin", "https://music.163.com");

                // 只添加有效的cookie
                if (cookie != null && !cookie.trim().isEmpty()) {
                    // 清理cookie，只保留关键cookie
                    String cleanedCookie = CookieUtil.deduplicateCookies(cookie);
                    if (!cleanedCookie.isEmpty()) {
                        requestBuilder.header("Cookie", cleanedCookie);
                    }
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(response, callback);
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    // 异步POST请求
    public static void sendPostRequest(String urlString, String json, final NetworkCallback callback) {
        executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(response, callback);
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    // 文件上传
    public static void uploadFile(String urlString, File file, final NetworkCallback callback) {
        executor.submit(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=----Boundary");

                try (var outputStream = connection.getOutputStream()) {
                    String boundary = "----Boundary";
                    String crlf = "\r\n";

                    // 写文件部分
                    outputStream.write(("--" + boundary + crlf).getBytes());
                    outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + crlf).getBytes());
                    outputStream.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes());

                    try (var fileInputStream = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }

                    outputStream.write((crlf + "--" + boundary + "--" + crlf).getBytes());
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (var inputStream = connection.getInputStream()) {
                        String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        if (callback != null) {
                            callback.onResponse(response);
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("Upload failed with code: " + responseCode);
                    }
                }
            } catch (Exception e) {
                handleException(e, callback);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // 文件下载
    public static void downloadFile(String urlString, final String savePath, final NetworkCallback callback) {
        executor.submit(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    File outputFile = new File(savePath);
                    try (InputStream inputStream = connection.getInputStream();
                         FileOutputStream fos = new FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();

                        if (callback != null) {
                            callback.onResponse("Download completed successfully.");
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("Download failed with code: " + responseCode);
                    }
                }
            } catch (Exception e) {
                handleException(e, callback);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // 处理响应
    private static void handleResponse(HttpResponse<String> response, NetworkCallback callback) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            if (callback != null) {
                callback.onResponse(response.body());
            }
        } else {
            if (callback != null) {
                callback.onFailure("Request failed with code: " + response.statusCode());
            }
        }
    }

    // 处理异常
    private static void handleException(Exception e, NetworkCallback callback) {
        if (callback != null) {
            callback.onFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 添加关闭方法
    public static void shutdown() {
        executor.shutdown();
    }

    // 网络请求回调接口
    public interface NetworkCallback {
        void onResponse(String response);
        void onFailure(String error);
    }
}