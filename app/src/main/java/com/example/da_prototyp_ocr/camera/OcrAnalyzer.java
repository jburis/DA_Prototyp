package com.example.da_prototyp_ocr.camera;

import android.annotation.SuppressLint;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OcrAnalyzer implements ImageAnalysis.Analyzer {

    public interface Callback {
        void onOcrText(@NonNull String fullText);
        void onOcrError(@NonNull Exception e);
        void onDone();
    }

    private final Callback callback;

    public OcrAnalyzer(@NonNull Callback callback) {
        this.callback = callback;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            callback.onDone();
            return;
        }

        InputImage img = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(img)
                .addOnSuccessListener(result -> callback.onOcrText(result.getText()))
                .addOnFailureListener(e -> callback.onOcrError(e instanceof Exception ? (Exception) e : new Exception(e)))
                .addOnCompleteListener(t -> {
                    imageProxy.close();
                    callback.onDone();
                });
    }
}