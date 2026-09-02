package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GPTS 类 - 使用 OkHttp 异步请求
 */
public class GPTS {

    private String apiKey;
    private String modelName;
    private float temperature;
    private float topP;
    private int maxTokens;

    private final List<String> supportedModelNames = Arrays.asList(
            "gpt-3.5-turbo",
            "gpt-4-turbo-preview",
            "gpt-4-turbo",
            "gpt-4-vision-preview",
            "gpt-4-all",
            "GPTS",
            "gpt-4-1106-preview",
            "gpt-4-0125-preview",
            "gpt-4-turbo-2024-04-09",
            "gpt-4o"
    );

    private String defaultPrompt = "You are a useful assistant who can efficiently complete user-specified tasks or answer user questions well.";
    private int defaultSleepTime = 2; // 秒（此示例暂未使用，如果需要重试，可用这个）
    private int maxRetries = 5;       // 最大重试次数（此示例暂未使用，如果需要重试，可在异步逻辑中实现）

    // API 地址（已替换为最新地址）
    private String url = "https://chatapi.onechats.top/v1/chat/completions";

    // 构造函数
    public GPTS(@Nullable String apiKey,
                String modelName,
                float temperature,
                float topP,
                int maxTokens) {
        this.apiKey = (apiKey != null) ? apiKey : System.getenv("OPENAI_API_KEY");
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key is missing!");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is missing!");
        }
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    /**
     * 动态设置字段值
     */
    public void setAttr(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        switch (name) {
            case "apiKey":
                this.apiKey = (String) value;
                break;
            case "modelName":
                this.modelName = (String) value;
                break;
            case "temperature":
                this.temperature = (float) value;
                break;
            case "topP":
                this.topP = (float) value;
                break;
            case "maxTokens":
                this.maxTokens = (int) value;
                break;
            case "defaultPrompt":
                this.defaultPrompt = (String) value;
                break;
            case "defaultSleepTime":
                this.defaultSleepTime = (int) value;
                break;
            case "maxRetries":
                this.maxRetries = (int) value;
                break;
            case "url":
                this.url = (String) value;
                break;
            default:
                throw new NoSuchFieldException("Field '" + name + "' does not exist.");
        }
    }

    /**
     * 生成默认 Payload
     */
    private JSONObject getPayload(@Nullable String prompt) throws JSONException {
        if (prompt == null || prompt.isEmpty()) {
            prompt = defaultPrompt;
        }
        JSONObject payload = new JSONObject();
        payload.put("model", modelName);
        payload.put("temperature", temperature);
        payload.put("top_p", topP);
        payload.put("max_tokens", maxTokens);

        // 构造 messages 数组
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");

        // system 内容 (type = text)
        JSONArray sysContent = new JSONArray();
        JSONObject sysText = new JSONObject();
        sysText.put("type", "text");
        sysText.put("text", prompt);
        sysContent.put(sysText);

        sysMsg.put("content", sysContent);
        messages.put(sysMsg);

        payload.put("messages", messages);
        return payload;
    }

    public String chatSync(String question,
                           @Nullable Object imageFiles,
                           @Nullable String prompt,
                           @Nullable JSONObject history) throws Exception {

        // 拼装 payload
        JSONObject payload = (history != null) ? history : getPayload(prompt);
        JSONArray messages = payload.getJSONArray("messages");

        // 追加用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        JSONArray userContent = new JSONArray();

        // 添加用户文本
        JSONObject userText = new JSONObject();
        userText.put("type", "text");
        userText.put("text", question);
        userContent.put(userText);

        // 如果有图像文件
        if (imageFiles != null) {
            processImageFiles(imageFiles, userContent);
        }

        userMsg.put("content", userContent);
        messages.put(userMsg);

        // 配置 OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)  // 连接超时
                .readTimeout(60, TimeUnit.SECONDS)    // 读取超时
                .writeTimeout(60, TimeUnit.SECONDS)   // 写入超时
                .build();

        // 构造请求体
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(JSON, payload.toString());

        // 构造 HTTP 请求
        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(getHeaders()))
                .post(body)
                .build();

        int retries = 0;
        boolean success = false;
        Response response = null;

        // 重试机制
        while (retries < maxRetries && !success) {
            try {
                response = client.newCall(request).execute(); // 同步执行请求
                if (response.isSuccessful()) {
                    // 请求成功，处理响应
                    success = true;
                    return handleResponseSync(response, payload);
                } else {
                    retries++;
                    if (retries < maxRetries) {
                        Thread.sleep(defaultSleepTime); // 等待一段时间后重试
                    } else {
                        throw new IOException("Unexpected response code: " + response.code());
                    }
                }
            } catch (IOException | InterruptedException e) {
                retries++;
                if (retries >= maxRetries) {
                    throw new IOException("Max retries reached.", e);
                }
                Thread.sleep(defaultSleepTime); // 等待一段时间后重试
            }
        }

        // 如果重试超过最大次数依然失败，抛出异常
        if (!success) {
            throw new IOException("Max retries reached.");
        }

        return ""; // 默认返回值，如果发生了异常，则抛出异常

    }

    /**
     * 处理成功响应并返回结果
     */
    private String handleResponseSync(Response response, JSONObject payload) throws IOException, JSONException {
        String respString = response.body() != null ? response.body().string() : "";
        JSONObject responseJson = new JSONObject(respString);

        // 解析结果
        String output = parseResponseSync(responseJson);

        // 将 assistant 的回复添加到历史上下文
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        JSONArray assistantContent = new JSONArray();
        JSONObject assistantText = new JSONObject();
        assistantText.put("type", "text");
        assistantText.put("text", output);
        assistantContent.put(assistantText);
        assistantMsg.put("content", assistantContent);

        payload.getJSONArray("messages").put(assistantMsg);

        return output;
    }

    /**
     * 解析返回的 JSON，提取最后一条 assistant 文本
     */
    private String parseResponseSync(JSONObject responseJson) throws JSONException {
        JSONArray choices = responseJson.getJSONArray("choices");
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject messageObj = firstChoice.getJSONObject("message");
        return messageObj.getString("content");
    }

    /**
     * 处理图像文件
     */
    private void processImageFiles(Object imageFiles, JSONArray userContent) throws Exception {
        String[] imagesArr;
        if (imageFiles instanceof String) {
            imagesArr = new String[]{(String) imageFiles};
        } else if (imageFiles instanceof String[]) {
            imagesArr = (String[]) imageFiles;
        } else {
            throw new IllegalArgumentException("imageFiles must be String or String[]");
        }

        // 压缩并编码每个图像
        for (String imgPath : imagesArr) {
            JSONObject imageObj = new JSONObject();
            imageObj.put("type", "image_url");
            String mimeType = guessMimeType(imgPath);
            String base64Img = compressAndEncodeImage(imgPath);
            JSONObject urlObj = new JSONObject();
            urlObj.put("url", "data:" + mimeType + ";base64," + base64Img);
            imageObj.put("image_url", urlObj);
            userContent.put(imageObj);
        }
    }

    /**
     * 压缩并编码图像
     */
    private String compressAndEncodeImage(String imgPath) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream); // 压缩质量 80%
        byte[] bytes = outputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }


    /**
     * 解析返回的 JSON，提取最后一条 assistant 文本
     */
    private String parseResponse(JSONObject responseJson) throws JSONException {
        JSONArray choices = responseJson.getJSONArray("choices");
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject messageObj = firstChoice.getJSONObject("message");
        return messageObj.getString("content");
    }

    /**
     * 获取请求头
     */
    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    /**
     * 将图像文件读取为 base64
     */
    private String encodeImage(String imagePath) throws IOException {
        File file = new File(imagePath);
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        int readBytes = fis.read(data);
        fis.close();
        if (readBytes != file.length()) {
            throw new IOException("Could not read the full file: " + imagePath);
        }
        // 使用 android.util.Base64
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    /**
     * 简易推断文件类型，可改用更可靠的库
     */
    private String guessMimeType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    /**
     * GPTS 结果类：包含输出文本和更新后的历史上下文
     */
    public static class GPTSResult {
        public String output;
        public JSONObject history;

        public GPTSResult(String output, JSONObject history) {
            this.output = output;
            this.history = history;
        }
    }
}
