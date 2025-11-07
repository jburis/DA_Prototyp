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

import android.util.Log;
import android.widget.Toast;import androidx.annotation.NonNull;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


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
        testApiConnection();
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
        // Wir extrahieren die Bestellnummer
        String order = NameExtractor.extractOrder(full);

        if (order != null) {
            // Wenn eine Bestellnummer gefunden wurde, starten wir den Check-in
            performCheckIn(order);
        } else {
            // Wenn keine Bestellnummer gefunden wurde, zeigen wir nur den erkannten Text an
            runOnUiThread(() -> {
                textResult.setText("Keine Bestellnummer erkannt.\n\n" + full);
                Toast.makeText(this, "Keine Bestellnummer im Text gefunden.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runQrScan(ImageProxy imageProxy) {
        Image img = imageProxy.getImage();
        if (img == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(img, imageProxy.getImageInfo().getRotationDegrees());
        BarcodeScanner scanner = BarcodeScanning.getClient();

        scanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        // Wir nehmen den ersten erkannten Barcode
                        String qrCodeValue = barcodes.get(0).getRawValue();
                        Log.d("QRSCAN", "QR-Code erkannt: " + qrCodeValue);

                        // Wir gehen davon aus, dass der QR-Code die Bestellnummer enthält,
                        // und starten den Check-in-Prozess.
                        if (qrCodeValue != null) {
                            performCheckIn(qrCodeValue);
                        }

                    } else {
                        Log.d("QRSCAN", "Kein QR-Code im Bild gefunden.");
                        runOnUiThread(() -> Toast.makeText(this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Log.e("QRSCAN", "QR-Code-Analyse fehlgeschlagen", e))
                // KORREKTUR: Verwende addOnCompleteListener, um das Bild immer freizugeben.
                .addOnCompleteListener(task -> imageProxy.close());
    }


    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startCamera();
    }

// ... innerhalb Ihrer Activity-Klasse (z.B. StartActivity)

    /**
     * Eine Testmethode, um die API-Verbindung zu überprüfen.
     * Sie versucht, die Liste aller Teilnehmer vom Server abzurufen.
     */
    private void testApiConnection() {
        // 1. ApiService-Instanz über den ApiClient erstellen
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // 2. API-Aufruf vorbereiten (getAttendees mit leerem Query-String, um alle zu bekommen)
        Call<List<Attendee>> call = apiService.getAttendees(null);

        // Loggen der Anfrage-URL zur Fehlersuche
        Log.d("APITEST", "Sende API-Anfrage an: " + call.request().url());

        // 3. Den Aufruf asynchron ausführen (damit die App nicht einfriert)
        call.enqueue(new Callback<List<Attendee>>() {
            @Override
            public void onResponse(@NonNull Call<List<Attendee>> call, @NonNull Response<List<Attendee>> response) {
                // Diese Methode wird immer aufgerufen, wenn der Server antwortet.

                // Wichtig: UI-Updates müssen auf dem Haupt-Thread laufen
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        // HTTP-Status 2xx (Erfolg)
                        List<Attendee> attendees = response.body();
                        if (attendees != null) {
                            String message = "API-Test ERFOLGREICH: " + attendees.size() + " Teilnehmer empfangen.";
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                            Log.i("APITEST", message);

                            // Optional: Den ersten Teilnehmer loggen, falls vorhanden
                            if (!attendees.isEmpty()) {
                                Log.i("APITEST", "Erster Teilnehmer: " + attendees.get(0).getParticipant());
                            }

                        }
                    } else {
                        // HTTP-Fehler (z.B. 404 Not Found, 500 Server Error)
                        String errorMessage = "API-Test FEHLGESCHLAGEN: Server antwortete mit Fehlercode " + response.code();
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        Log.e("APITEST", errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<List<Attendee>> call, @NonNull Throwable t) {
                // Diese Methode wird bei einem Netzwerkfehler aufgerufen
                // (z.B. keine Verbindung, falsche IP, Server offline).

                runOnUiThread(() -> {
                    String failureMessage = "API-Test FEHLGESCHLAGEN: Netzwerkfehler.";
                    Toast.makeText(getApplicationContext(), failureMessage, Toast.LENGTH_LONG).show();
                    Log.e("APITEST", "Netzwerkfehler: " + t.getMessage(), t);
                });
            }
        });
    }


    /**
     * Führt einen Check-in für eine gegebene Bestellnummer über die API durch.
     * @param orderNumber Die zu checkende Bestellnummer.
     */
    private void performCheckIn(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("APICHECKIN", "Starte Check-in für Bestellnummer: " + orderNumber);

        // 1. ApiService-Instanz holen
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // 2. Request-Body erstellen (wir checken standardmäßig eine Person ein)
        CheckInRequest checkInRequest = new CheckInRequest(1);

        // 3. API-Aufruf vorbereiten
        Call<Attendee> call = apiService.checkIn(orderNumber, checkInRequest);

        // 4. Aufruf asynchron ausführen
        call.enqueue(new Callback<Attendee>() {
            @Override
            public void onResponse(@NonNull Call<Attendee> call, @NonNull Response<Attendee> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        // ERFOLG: Der Server hat mit 2xx geantwortet und einen Teilnehmer zurückgegeben
                        Attendee checkedInAttendee = response.body();
                        String successMessage = "Check-in für " + checkedInAttendee.getParticipant() + " erfolgreich!";
                        Log.i("APICHECKIN", successMessage + " (Neue Anzahl: " + checkedInAttendee.getCheckedInCount() + ")");
                        Toast.makeText(MainActivity.this, successMessage, Toast.LENGTH_LONG).show();

                        // UI aktualisieren mit der Erfolgsmeldung
                        textResult.setText(successMessage + "\nEingecheckt: " + checkedInAttendee.getCheckedInCount() + "/" + checkedInAttendee.getGuestCount());

                    } else {
                        // FEHLER: Der Server hat einen Fehlercode gesendet (z.B. 404 Not Found)
                        String errorMessage;
                        if (response.code() == 404) {
                            errorMessage = "Fehler: Bestellnummer '" + orderNumber + "' nicht gefunden.";
                        } else {
                            errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                        }
                        Log.e("APICHECKIN", errorMessage);
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        textResult.setText(errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<Attendee> call, @NonNull Throwable t) {
                // FEHLER: Netzwerkproblem
                runOnUiThread(() -> {
                    String failureMessage = "Netzwerkfehler: " + t.getMessage();
                    Log.e("APICHECKIN", failureMessage, t);
                    Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                    textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                });
            }
        });
    }

}
