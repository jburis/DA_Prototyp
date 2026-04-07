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

/**
 * Verwaltet die Kamera über CameraX.
 * Kümmert sich um das Starten, Stoppen und die Bildanalyse-Pipeline.
 */
public class CameraController {

    private static final String TAG = "CameraController";

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;

    /**
     * Startet die Kamera mit Live-Preview und Bildanalyse.
     *
     * @param owner       Activity oder Fragment (für Lifecycle-Binding)
     * @param previewView Die View wo das Kamerabild angezeigt wird
     * @param lensFacing  Welche Kamera (CameraSelector.LENS_FACING_BACK oder FRONT)
     * @param targetSize  Gewünschte Auflösung (z.B. 720x720)
     */
    public void start(@NonNull LifecycleOwner owner,
                      @NonNull PreviewView previewView,
                      int lensFacing,
                      @NonNull Size targetSize) {

        // CameraProvider asynchron holen - dauert kurz beim ersten Mal
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(previewView.getContext());

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                // Preview für die Live-Ansicht konfigurieren
                Preview preview = new Preview.Builder()
                        .setTargetResolution(targetSize)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // ImageAnalysis für OCR/QR-Scanning konfigurieren
                // KEEP_ONLY_LATEST = wenn Verarbeitung zu langsam, Frames überspringen
                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetSize)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                // Kamera auswählen (vorne oder hinten)
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                // Alte Bindings aufräumen und neu starten
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(owner, selector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Kamera konnte nicht gestartet werden", e);
            } catch (Exception e) {
                Log.e(TAG, "Unerwarteter Fehler beim Kamerastart", e);
            }
        }, ContextCompat.getMainExecutor(previewView.getContext()));
    }

    /**
     * Gibt die ImageAnalysis zurück, um einen Analyzer (z.B. CombinedAnalyzer) anzuhängen.
     */
    public ImageAnalysis getImageAnalysis() {
        return imageAnalysis;
    }

    /**
     * Stoppt die Kamera und gibt Ressourcen frei.
     */
    public void stop() {
        try {
            if (cameraProvider != null) cameraProvider.unbindAll();
        } catch (Exception ignored) {
            // Fehler beim Stoppen ignorieren - passiert manchmal bei schnellem Activity-Wechsel
        }
    }
}