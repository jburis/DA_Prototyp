package com.example.da_prototyp_ocr.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.camera.CameraController;
import com.example.da_prototyp_ocr.camera.OcrAnalyzer;
import com.example.da_prototyp_ocr.camera.QrAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceCheckInActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "AttendanceCheckIn";

    // Views
    private PreviewView previewView;
    private View cameraCard;
    private TextView textResult;
    private TextView attendeeNameText, attendeeSeatsText;

    private Button btnToggleCamera;
    private Button scanBtn;
    private Button qrBtn;
    private ImageButton btnAddManual;

    private ListView scannedList;
    private ArrayAdapter<String> scannedAdapter;
    private final List<String> scannedItems = new ArrayList<>();

    // Camera
    private final CameraController cameraController = new CameraController();
    private ExecutorService analyzerExecutor;
    private boolean isCameraOn = false;
    private volatile boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind
        previewView = findViewById(R.id.previewView);
        cameraCard = findViewById(R.id.cameraCard);

        textResult = findViewById(R.id.textResult);
        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);

        btnToggleCamera = findViewById(R.id.btnToggleCamera);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);
        btnAddManual = findViewById(R.id.btnAddManual);

        scannedList = findViewById(R.id.scannedList);
        scannedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scannedItems);
        scannedList.setAdapter(scannedAdapter);

        analyzerExecutor = Executors.newSingleThreadExecutor();

        // Dummy Daten (10 Stück)
        seedDummyList();

        // Kamera STARTET NICHT automatisch
        setCameraUi(false);

        // Toggle Kamera
        btnToggleCamera.setOnClickListener(v -> toggleCamera());

        // OCR Scan (demo: startet Analyzer)
        scanBtn.setOnClickListener(v -> {
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            startOneShotOcr();
        });

        // QR Scan (demo: startet Analyzer)
        qrBtn.setOnClickListener(v -> {
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            startOneShotQr();
        });

        // Plus-Symbol: Dummy-Teilnehmer hinzufügen (sichtbarer Test)
        btnAddManual.setOnClickListener(v -> {
            scannedItems.add(0, "Neuer Teilnehmer – 00000");
            scannedAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Dummy hinzugefügt", Toast.LENGTH_SHORT).show();
        });

        // Optional: Long-Press löschen (praktisch)
        scannedList.setOnItemLongClickListener((parent, view, position, id) -> {
            String removed = scannedItems.remove(position);
            scannedAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Gelöscht: " + removed, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void seedDummyList() {
        scannedItems.clear();
        scannedItems.add("Max Mustermann – 12345");
        scannedItems.add("Anna Huber – 23456");
        scannedItems.add("Peter Klein – 34567");
        scannedItems.add("Sophie Wagner – 45678");
        scannedItems.add("Lukas Mayer – 56789");
        scannedItems.add("Julia Bauer – 67890");
        scannedItems.add("Daniel Schmidt – 78901");
        scannedItems.add("Lisa Fischer – 89012");
        scannedItems.add("Martin Hofer – 90123");
        scannedItems.add("Clara Weiss – 01234");
        scannedAdapter.notifyDataSetChanged();
    }

    // ===== Kamera Toggle / UI =====

    private void toggleCamera() {
        if (isCameraOn) stopCamera();
        else startCameraSafe();
    }

    private void startCameraSafe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startCamera();
    }

    private void startCamera() {
        cameraController.start(
                this,
                previewView,
                CameraSelector.LENS_FACING_BACK,
                new Size(640, 480)
        );

        isCameraOn = true;
        isProcessing = false;
        setCameraUi(true);
        textResult.setText("Kamera aktiv – halte QR/Bestellnummer ins Bild");
    }

    private void stopCamera() {
        try {
            ImageAnalysis analysis = cameraController.getImageAnalysis();
            if (analysis != null) analysis.clearAnalyzer();
            cameraController.stop();
        } catch (Exception e) {
            Log.e(TAG, "stopCamera failed", e);
        }

        isCameraOn = false;
        isProcessing = false;
        setCameraUi(false);
        textResult.setText("Kamera manuell starten und dann scannen");
    }

    private void setCameraUi(boolean on) {
        // WICHTIG: Preview ausblenden
        cameraCard.setVisibility(on ? View.VISIBLE : View.GONE);
        btnToggleCamera.setText(on ? "Kamera AUS" : "Kamera AN");
    }

    // ===== Permission =====

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Berechtigung OK. Du kannst Kamera einschalten.", Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQ_CAMERA) {
            Toast.makeText(this, "Kamera-Berechtigung verweigert", Toast.LENGTH_LONG).show();
        }
    }

    // ===== Analyzer: One-shot OCR =====

    private void startOneShotOcr() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) {
            Toast.makeText(this, "Kamera nicht bereit.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isProcessing) return;

        isProcessing = true;
        analysis.clearAnalyzer();

        analysis.setAnalyzer(analyzerExecutor, new OcrAnalyzer(new OcrAnalyzer.Callback() {
            @Override
            public void onOcrText(@NonNull String fullText) {
                runOnUiThread(() -> {
                    // Demo-Ausgabe (du kannst hier wieder deine Bestellnummer-Logik einsetzen)
                    attendeeNameText.setText("Name: (OCR erkannt)");
                    attendeeSeatsText.setText("Bestellnummer / Plätze: (OCR Text da)");

                    scannedItems.add(0, "OCR Scan – " + System.currentTimeMillis());
                    scannedAdapter.notifyDataSetChanged();

                    textResult.setText("OCR Scan OK");
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onOcrError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> finishProcessing(analysis));
            }
        }));
    }

    // ===== Analyzer: One-shot QR =====

    private void startOneShotQr() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) {
            Toast.makeText(this, "Kamera nicht bereit.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isProcessing) return;

        isProcessing = true;
        analysis.clearAnalyzer();

        analysis.setAnalyzer(analyzerExecutor, new QrAnalyzer(new QrAnalyzer.Callback() {
            @Override
            public void onQrRaw(@NonNull String rawValue) {
                runOnUiThread(() -> {
                    attendeeNameText.setText("Name: (QR erkannt)");
                    attendeeSeatsText.setText("Bestellnummer / Plätze: (QR raw)");

                    scannedItems.add(0, "QR Scan – " + rawValue);
                    scannedAdapter.notifyDataSetChanged();

                    textResult.setText("QR Scan OK");
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onQrEmpty() {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onQrError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "QR Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> finishProcessing(analysis));
            }
        }));
    }

    private void finishProcessing(@NonNull ImageAnalysis analysis) {
        try { analysis.clearAnalyzer(); } catch (Exception ignored) {}
        isProcessing = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isCameraOn) stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (analyzerExecutor != null) analyzerExecutor.shutdown(); } catch (Exception ignored) {}
    }
}