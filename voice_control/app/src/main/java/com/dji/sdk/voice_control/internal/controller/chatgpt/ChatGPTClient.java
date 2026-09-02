package com.dji.sdk.voice_control.internal.controller.chatgpt;// dashscope SDK的版本 >= 2.19.0
import java.util.*;

import com.alibaba.dashscope.common.Role;
import com.dji.sdk.voice_control.BuildConfig;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.exception.InputRequiredException;
import java.lang.System;
import com.alibaba.dashscope.utils.Constants;

public class ChatGPTClient {
    private static StringBuilder reasoningContent = new StringBuilder();
    private static StringBuilder finalContent = new StringBuilder();
    private static boolean isFirstPrint = true;

    private static void handleGenerationResult(MultiModalConversationResult message) {
        String re = message.getOutput().getChoices().get(0).getMessage().getReasoningContent();
        String reasoning = Objects.isNull(re)?"":re; // 默认值

        List<Map<String, Object>> content = message.getOutput().getChoices().get(0).getMessage().getContent();
        if (!reasoning.isEmpty()) {
            reasoningContent.append(reasoning);
            if (isFirstPrint) {
                System.out.println("====================思考过程====================");
                isFirstPrint = false;
            }
            System.out.print(reasoning);
        }

        if (Objects.nonNull(content) && !content.isEmpty()) {
            Object text = content.get(0).get("text");
            finalContent.append(text);
            if (!isFirstPrint) {
                System.out.println("\n====================完整回复====================");
                isFirstPrint = true;
            }
            System.out.print(text);
        }
    }
    
    /**
     * Ask a question about an image and return the response
     * 
     * @param question The question text to ask about the image
     * @param imageFilePath The local file path to the image
     * @return The response from the AI model
     * @throws NoApiKeyException If no API key is found
     * @throws ApiException If there's an API error
     * @throws InputRequiredException If required input is missing
     * @throws UploadFileException If there's an error uploading the file
     */
    public static String askWithImage(String question, String imageFilePath) 
            throws NoApiKeyException, ApiException, InputRequiredException, UploadFileException {
        // Reset the content builders before each request
        reasoningContent.setLength(0);
        finalContent.setLength(0);
        isFirstPrint = true;
        
        // Format image path to include the file:// prefix if not already present
        String formattedImagePath = imageFilePath;
        if (!imageFilePath.startsWith("file://")) {
            formattedImagePath = "file://" + imageFilePath;
        }
        
        MultiModalConversation conv = new MultiModalConversation();
        String finalFormattedImagePath = formattedImagePath;
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                    new HashMap<String, Object>(){{put("image", finalFormattedImagePath);}},
                    new HashMap<String, Object>(){{put("text", question);}}
                )).build();
        
        streamCallWithMessage(conv, userMessage);
        
        // Return the final content as the result
        return finalContent.toString();
    }
    
    public static MultiModalConversationParam buildMultiModalConversationParam(MultiModalMessage Msg)  {
        if (BuildConfig.DASHSCOPE_API_KEY == null || BuildConfig.DASHSCOPE_API_KEY.trim().isEmpty()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is not configured in local.properties");
        }
        return MultiModalConversationParam.builder()
                .apiKey(BuildConfig.DASHSCOPE_API_KEY)
                .model(BuildConfig.DASHSCOPE_MODEL)
                .messages(Arrays.asList(Msg))
                .incrementalOutput(true)
                .build();
    }

    public static void streamCallWithMessage(MultiModalConversation conv, MultiModalMessage Msg)
            throws NoApiKeyException, ApiException, InputRequiredException, UploadFileException {
        MultiModalConversationParam param = buildMultiModalConversationParam(Msg);
        Flowable<MultiModalConversationResult> result = conv.streamCall(param);
        result.blockingForEach(message -> {
            handleGenerationResult(message);
        });
    }
    
    public static void main(String[] args) {
        try {
            String localPath = "xxx/test.png";
            String filePath = "file://"+ localPath;
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                    .content(Arrays.asList(new HashMap<String, Object>(){{put("image", filePath);}},
                            new HashMap<String, Object>(){{put("text", "请解答这道题");}})).build();
            streamCallWithMessage(conv, userMessage);
//             打印最终结果
//            if (reasoningContent.length() > 0) {
//                System.out.println("\n====================完整回复====================");
//                System.out.println(finalContent.toString());
//            }
        } catch (ApiException | NoApiKeyException | UploadFileException | InputRequiredException e) {
        }
        System.exit(0);
    }
}
