package com.example.da_prototyp_ocr.camera;

import android.annotation.SuppressLint;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

public class QrAnalyzer implements ImageAnalysis.Analyzer {

    public interface Callback {
        void onQrRaw(@NonNull String rawValue);
        void onQrEmpty();
        void onQrError(@NonNull Exception e);
        void onDone();
    }

    private final Callback callback;

    public QrAnalyzer(@NonNull Callback callback) {
        this.callback = callback;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image img = imageProxy.getImage();
        if (img == null) {
            imageProxy.close();
            callback.onDone();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                img,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        BarcodeScanning.getClient()
                .process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes == null || barcodes.isEmpty() || barcodes.get(0).getRawValue() == null) {
                        callback.onQrEmpty();
                    } else {
                        callback.onQrRaw(barcodes.get(0).getRawValue());
                    }
                })
                .addOnFailureListener(e -> callback.onQrError(e instanceof Exception ? (Exception) e : new Exception(e)))
                .addOnCompleteListener(t -> {
                    imageProxy.close();
                    callback.onDone();
                });
    }
}