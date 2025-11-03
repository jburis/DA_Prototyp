package com.example.da_prototyp_ocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
// ACHTUNG: Barcode nur aus .common importieren!
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 100;
    private PreviewView previewView;
    private Button scanBtn, qrBtn;
    private ExecutorService cameraExecutor;
    private ImageAnalysis analysis;

    private TextView textResult;
    private boolean scanQrMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textResult = findViewById(R.id.textResult);
        previewView = findViewById(R.id.previewView);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr); // QR-Code-Button
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        scanBtn.setOnClickListener(v -> {
            scanQrMode = false;
            analyzeOnce();
        });

        qrBtn.setOnClickListener(v -> {
            scanQrMode = true;
            analyzeOnce();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Kamera-Start fehlgeschlagen", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeOnce() {
        if (analysis == null) return;
        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (scanQrMode) {
                runQrScan(imageProxy);
            } else {
                runOcrOnFrame(imageProxy);
            }
            // Nach einem Scan Analyzer wieder entfernen für "einmalig"
            analysis.clearAnalyzer();
        });
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runOcrOnFrame(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }

        InputImage img = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(img)
                .addOnSuccessListener(this::handleResult)
                .addOnFailureListener(e -> Toast.makeText(this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void handleResult(Text visionText) {
        String full = visionText.getText();
        String order = NameExtractor.extractOrder(full);
        String name  = NameExtractor.extractName(full);

        runOnUiThread(() -> {
            if (order != null || name != null) {
                StringBuilder sb = new StringBuilder();
                if (order != null) sb.append("Bestellnummer: ").append(order).append("\n");
                if (name  != null) sb.append("Name: ").append(name);
                sb.append("\n\n(Voller OCR-Text unten)\n").append(full);
                textResult.setText(sb.toString());
            } else if (full.trim().isEmpty()) {
                textResult.setText("Kein Text erkannt.");
            } else {
                textResult.setText(full);
            }
        });
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runQrScan(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }

        InputImage img = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        scanner.process(img)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty()) {
                        runOnUiThread(() -> textResult.setText("Kein QR-Code erkannt."));
                    } else {
                        String qr = barcodes.get(0).getRawValue();
                        runOnUiThread(() -> textResult.setText("QR erkannt: " + qr));
                    }
                })
                .addOnFailureListener(e -> runOnUiThread(() -> textResult.setText("QR-Scan Fehler: " + e.getMessage())))
                .addOnCompleteListener(t -> imageProxy.close());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startCamera();
    }
}
