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
 * Das Herzstück der Erkennung: Analysiert jeden Kamera-Frame auf QR-Codes und Bestellnummern.
 *
 * Warum kombiniert? Ursprünglich gab es zwei separate Buttons (QR-Scan und OCR-Scan),
 * aber das war umständlich für einhändige Bedienung. Jetzt läuft beides automatisch:
 *
 * 1. Erst QR-Code checken (schneller und eindeutiger)
 * 2. Falls kein QR → OCR auf Bestellnummer (#123456) versuchen
 * 3. Bei Treffer → 3 Sekunden Pause, damit nicht dieselbe Person 10x erkannt wird
 */
public class CombinedAnalyzer implements ImageAnalysis.Analyzer {

    // Nach erfolgreicher Erkennung kurz warten, sonst piept es endlos
    private static final long COOLDOWN_MS = 3000;

    public enum ResultType {
        QR_CODE,          // Klubkarte gescannt
        OCR_ORDER_NUMBER  // Bestellnummer auf Buchungsbestätigung erkannt
    }

    public interface Callback {
        /**
         * Wird aufgerufen wenn was erkannt wurde.
         * @param type Was wurde erkannt (QR oder OCR)
         * @param value Bei QR: Name aus Klubkarte, bei OCR: die Bestellnummer
         */
        void onResult(@NonNull ResultType type, @NonNull String value);

        void onError(@NonNull Exception e);
    }

    private final Callback callback;

    // Flags um Race Conditions zu vermeiden
    private volatile boolean isProcessing = false;
    private volatile long lastResultTime = 0;
    private volatile String lastResultValue = null;

    public CombinedAnalyzer(@NonNull Callback callback) {
        this.callback = callback;
    }

    /**
     * Wird von CameraX für JEDEN Frame aufgerufen (ca. 30x pro Sekunde).
     * Muss schnell sein, sonst laggt die Kamera-Preview.
     */
    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        // Noch am Verarbeiten oder gerade erst was erkannt? → Frame wegwerfen
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

        // Bild für ML Kit vorbereiten (inkl. Rotation, sonst ist alles auf der Seite)
        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        // Erst QR versuchen - ist schneller und eindeutiger als OCR
        BarcodeScanning.getClient()
                .process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    String qrResult = extractQrResult(barcodes);

                    if (qrResult != null) {
                        // QR gefunden → fertig
                        handleResult(ResultType.QR_CODE, qrResult, imageProxy);
                    } else {
                        // Kein QR → OCR als Fallback versuchen
                        tryOcr(inputImage, imageProxy);
                    }
                })
                .addOnFailureListener(e -> {
                    // QR-Scanner crashed manchmal bei schlechtem Licht → OCR trotzdem versuchen
                    tryOcr(inputImage, imageProxy);
                });
    }

    /**
     * OCR-Fallback: Sucht nach Bestellnummer (#123456) im erkannten Text.
     */
    private void tryOcr(@NonNull InputImage inputImage, @NonNull ImageProxy imageProxy) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnSuccessListener(result -> {
                    String fullText = result.getText();
                    // NameExtractor sucht nach dem Muster "#" + mindestens 5 Ziffern
                    String orderNumber = NameExtractor.extractOrder(fullText);

                    if (orderNumber != null && !orderNumber.isEmpty()) {
                        handleResult(ResultType.OCR_ORDER_NUMBER, orderNumber, imageProxy);
                    } else {
                        // Nichts gefunden, nächster Frame
                        finishProcessing(imageProxy);
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError(e instanceof Exception ? (Exception) e : new Exception(e));
                    finishProcessing(imageProxy);
                });
    }

    /**
     * Extrahiert den Namen aus QR-Code der Klubkarte.
     * Format: "MitgliedsID;Geschlecht;;Vorname;Nachname"
     * z.B. "201806618;W;;Christine;FUHRMANN" → "Christine FUHRMANN"
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

        return NameExtractor.extractQRName(rawValue);
    }

    /**
     * Erfolgreiches Ergebnis verarbeiten.
     */
    private void handleResult(@NonNull ResultType type, @NonNull String value, @NonNull ImageProxy imageProxy) {
        // Duplikat-Check: Wenn gleiche Person noch im Cooldown → ignorieren
        // Verhindert dass jemand 5x hintereinander eingecheckt wird
        if (value.equals(lastResultValue) && isInCooldown()) {
            finishProcessing(imageProxy);
            return;
        }

        // Für nächsten Duplikat-Check merken
        lastResultValue = value;
        lastResultTime = System.currentTimeMillis();

        callback.onResult(type, value);
        finishProcessing(imageProxy);
    }

    private boolean isInCooldown() {
        return System.currentTimeMillis() - lastResultTime < COOLDOWN_MS;
    }

    private void finishProcessing(@NonNull ImageProxy imageProxy) {
        try {
            imageProxy.close();
        } catch (Exception ignored) {
            // Kann passieren wenn Kamera schon gestoppt wurde
        }
        isProcessing = false;
    }

    /**
     * Cooldown zurücksetzen - wird nach erfolgreichem Check-In aufgerufen,
     * damit sofort die nächste Person gescannt werden kann.
     */
    public void resetCooldown() {
        lastResultTime = 0;
        lastResultValue = null;
    }
}