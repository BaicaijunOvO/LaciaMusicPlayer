package ovo.baicaijun.laciamusicplayer.util;

import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.minecraft.client.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    // 异步GET请求
    public static void sendGetRequest(String urlString, final NetworkCallback callback) {
        executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(response, callback);
            } catch (Exception e) {
                handleException(e, callback);
            }
        });
    }

    public static void sendGetRequest(String urlString, String cookie, final NetworkCallback callback) {
        executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlString))
                        .header("Cookie", cookie)
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

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

    // 文件上传 - 使用传统的 HttpURLConnection（因为 java.net.http 对 multipart 支持有限）
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

    // 文件下载 - 使用传统的 HttpURLConnection（更好的流控制）
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

    // 网络请求回调接口（保持不变）
    public interface NetworkCallback {
        void onResponse(String response);
        void onFailure(String error);
    }
}