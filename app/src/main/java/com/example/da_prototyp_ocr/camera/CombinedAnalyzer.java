package com.example.da_prototyp_ocr.camera;

import android.annotation.SuppressLint;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.da_prototyp_ocr.logic.NameExtractor;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;

/**
 * Kombinierter Analyzer der automatisch sowohl QR-Codes als auch
 * Bestellnummern (OCR) erkennt.
 *
 * Ablauf pro Frame:
 * 1. Versuche QR-Code zu erkennen (schnell, eindeutig)
 * 2. Falls kein QR gefunden → versuche OCR (Bestellnummer)
 * 3. Bei Treffer → Callback auslösen + Cooldown starten
 */
public class CombinedAnalyzer implements ImageAnalysis.Analyzer {

    private static final long COOLDOWN_MS = 3000; // 3 Sekunden Pause nach Erkennung

    public enum ResultType {
        QR_CODE,
        OCR_ORDER_NUMBER
    }

    public interface Callback {
        /**
         * Wird aufgerufen wenn ein QR-Code oder eine Bestellnummer erkannt wurde.
         * @param type Art der Erkennung (QR oder OCR)
         * @param value Bei QR: extrahierter Name, bei OCR: Bestellnummer
         */
        void onResult(@NonNull ResultType type, @NonNull String value);

        /**
         * Wird bei Fehlern aufgerufen.
         */
        void onError(@NonNull Exception e);
    }

    private final Callback callback;
    private volatile boolean isProcessing = false;
    private volatile long lastResultTime = 0;
    private volatile String lastResultValue = null;

    public CombinedAnalyzer(@NonNull Callback callback) {
        this.callback = callback;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        // Wenn noch in Verarbeitung oder im Cooldown → Frame überspringen
        if (isProcessing || isInCooldown()) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        // SCHRITT 1: Versuche QR-Code zu erkennen
        BarcodeScanning.getClient()
                .process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    String qrResult = extractQrResult(barcodes);

                    if (qrResult != null) {
                        // QR-Code gefunden!
                        handleResult(ResultType.QR_CODE, qrResult, imageProxy);
                    } else {
                        // Kein QR → versuche OCR
                        tryOcr(inputImage, imageProxy);
                    }
                })
                .addOnFailureListener(e -> {
                    // QR fehlgeschlagen → versuche trotzdem OCR
                    tryOcr(inputImage, imageProxy);
                });
    }

    /**
     * Versucht eine Bestellnummer per OCR zu erkennen.
     */
    private void tryOcr(@NonNull InputImage inputImage, @NonNull ImageProxy imageProxy) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnSuccessListener(result -> {
                    String fullText = result.getText();
                    String orderNumber = NameExtractor.extractOrder(fullText);

                    if (orderNumber != null && !orderNumber.isEmpty()) {
                        // Bestellnummer gefunden!
                        handleResult(ResultType.OCR_ORDER_NUMBER, orderNumber, imageProxy);
                    } else {
                        // Nichts gefunden → Frame freigeben
                        finishProcessing(imageProxy);
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError(e instanceof Exception ? (Exception) e : new Exception(e));
                    finishProcessing(imageProxy);
                });
    }

    /**
     * Extrahiert den Namen aus einem QR-Code (falls gültiges Format).
     */
    @Nullable
    private String extractQrResult(@Nullable List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) {
            return null;
        }

        String rawValue = barcodes.get(0).getRawValue();
        if (rawValue == null || rawValue.isEmpty()) {
            return null;
        }

        // Versuche Namen aus QR-Code zu extrahieren (Semikolon-Format)
        String name = NameExtractor.extractQRName(rawValue);
        return name;
    }

    /**
     * Verarbeitet ein erfolgreiches Ergebnis.
     */
    private void handleResult(@NonNull ResultType type, @NonNull String value, @NonNull ImageProxy imageProxy) {
        // Prüfe ob es das gleiche Ergebnis wie beim letzten Mal ist (Duplikat-Schutz)
        if (value.equals(lastResultValue) && isInCooldown()) {
            finishProcessing(imageProxy);
            return;
        }

        // Ergebnis speichern für Duplikat-Prüfung
        lastResultValue = value;
        lastResultTime = System.currentTimeMillis();

        // Callback auslösen
        callback.onResult(type, value);

        finishProcessing(imageProxy);
    }

    /**
     * Prüft ob wir noch im Cooldown sind (nach letzter Erkennung).
     */
    private boolean isInCooldown() {
        return System.currentTimeMillis() - lastResultTime < COOLDOWN_MS;
    }

    /**
     * Beendet die Verarbeitung und gibt das Frame frei.
     */
    private void finishProcessing(@NonNull ImageProxy imageProxy) {
        try {
            imageProxy.close();
        } catch (Exception ignored) {}
        isProcessing = false;
    }

    /**
     * Setzt den Cooldown zurück (z.B. nach erfolgreichem Check-In).
     */
    public void resetCooldown() {
        lastResultTime = 0;
        lastResultValue = null;
    }
}
