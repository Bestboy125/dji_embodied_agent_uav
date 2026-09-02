package com.dji.sdk.voice_control.internal.controller.interfaces;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;

import com.dji.sdk.voice_control.internal.controller.ControlActivity;

import java.io.File;

import dji.common.flightcontroller.LocationCoordinate3D;

public interface ControlActivityCallback {
    /**
     * 在主界面上添加文本消息
     *
     * @param owner   消息的发送者，比如 "OWNER_BOT" 或 "OWNER_HUMAN"
     * @param message 消息内容
     */
    void addChatMessage(String owner, String message);

    /**
     * 在主界面上添加图片消息
     *
     * @param owner 消息的发送者
     * @param image 要显示的图片
     */
    void addChatMessage(String owner, Bitmap image);


    File saveBitmapAsFile(Bitmap bitmap, String filename);

    void showSelectObjectDialog();

    /**
     * 显示用于深度估计的对象选择对话框
     */
    void showSelectObjectDialogForDepthEstimation();

    String sendQuestionToGPTSync(String question, File file, boolean isHistory) throws Exception;

    Resources mgetResources();

    File mgetCacheDir();

    Context getContext();

    boolean getisFlying();

    LocationCoordinate3D getDroneLocation();

    float getHeading();

    float gerAltitude();

    int getBatteryPercent();

    int getTextsureViewWidth();

    int getTextsureViewHeight();

    void CaptureDjiImage(ControlActivity.CaptureImageCallback callback);

    File CaptureImage();
}

