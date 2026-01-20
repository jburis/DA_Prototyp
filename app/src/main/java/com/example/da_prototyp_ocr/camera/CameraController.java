package com.example.da_prototyp_ocr.camera;

import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class CameraController {

    private static final String TAG = "CameraController";

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;

    public void start(@NonNull LifecycleOwner owner,
                      @NonNull PreviewView previewView,
                      int lensFacing,
                      @NonNull Size targetSize) {

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(previewView.getContext());

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(targetSize)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetSize)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(owner, selector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "start() failed", e);
            } catch (Exception e) {
                Log.e(TAG, "start() unexpected error", e);
            }
        }, ContextCompat.getMainExecutor(previewView.getContext()));
    }

    public ImageAnalysis getImageAnalysis() {
        return imageAnalysis;
    }

    public void stop() {
        try {
            if (cameraProvider != null) cameraProvider.unbindAll();
        } catch (Exception ignored) {}
    }
}