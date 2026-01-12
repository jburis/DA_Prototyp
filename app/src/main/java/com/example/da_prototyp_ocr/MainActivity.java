package com.example.da_prototyp_ocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // --- UI & State Variablen ---
    private LinearLayout confirmationLayout;
    private TextView attendeeNameText, attendeeSeatsText;
    private Button confirmCheckInBtn;
    private List<Attendee> allAttendees = new ArrayList<>();
    private Attendee currentScannedAttendee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Initialisierungen ---
        textResult = findViewById(R.id.textResult);
        previewView = findViewById(R.id.previewView);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);
        cameraExecutor = Executors.newSingleThreadExecutor();

        confirmationLayout = findViewById(R.id.confirmationLayout);
        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        confirmationLayout.setVisibility(View.GONE);

        // Kamera-Berechtigung
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        // --- KORRIGIERTE CLICK-LISTENER ---
        scanBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            // Lade die Liste VOR dem Scan und starte den Scan danach
            loadAllAttendees(() -> {
                scanQrMode = false;
                analyzeOnce();
            });
        });

        qrBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            // Lade die Liste VOR dem Scan und starte den Scan danach
            loadAllAttendees(() -> {
                scanQrMode = true;
                analyzeOnce();
            });
        });
        startActivity(new Intent(this, PdfImportActivity.class));

        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedAttendee != null) {
                performCheckIn(currentScannedAttendee.getOrderNumber());
                confirmationLayout.setVisibility(View.GONE);
                currentScannedAttendee = null;
            }
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
            analysis.clearAnalyzer();
        });
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runOcrOnFrame(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
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

        if (order != null) {
            searchAttendeeLocally(order);
        } else {
            String name = NameExtractor.extractName(full);
            if (name != null) {
                performCheckInByName(name);
            } else {
                runOnUiThread(() -> {
                    textResult.setText("Keine Bestellnummer oder Name erkannt.\n\n" + full);
                    Toast.makeText(this, "Keine relevanten Daten im Text gefunden.", Toast.LENGTH_SHORT).show();
                });
            }
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
                        String qrCodeValue = barcodes.get(0).getRawValue();
                        Log.d("QRSCAN", "QR-Code Rohdaten: " + qrCodeValue);
                        String participantName = NameExtractor.extractQRName(qrCodeValue);
                        if (participantName != null) {
                            Log.d("QRSCAN", "Extrahierter Teilnehmer aus QR-Code: " + participantName);
                            performCheckInByName(participantName);
                        } else {
                            runOnUiThread(() -> {
                                String feedback = "QR-Code hat nicht das erwartete Format.";
                                if (qrCodeValue != null) feedback += "\nInhalt: " + qrCodeValue;
                                textResult.setText(feedback);
                                Toast.makeText(this, "QR-Code hat falsches Format.", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        Log.d("QRSCAN", "Kein QR-Code im Bild gefunden.");
                        runOnUiThread(() -> Toast.makeText(this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Log.e("QRSCAN", "QR-Code-Analyse fehlgeschlagen", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startCamera();
    }

    // --- KORRIGIERT: Lädt Teilnehmer und führt danach eine Aktion aus ---
    /**
     * Lädt alle Teilnehmer vom Server und führt danach eine Aktion aus (Callback).
     * @param onFinished Die Aktion, die nach erfolgreichem Laden ausgeführt werden soll (oder null).
     */
    private void loadAllAttendees(Runnable onFinished) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<List<Attendee>> call = apiService.getAttendees(null);
        Log.d("APILOAD", "Lade alle Teilnehmer vom Server...");

        call.enqueue(new Callback<List<Attendee>>() {
            @Override
            public void onResponse(@NonNull Call<List<Attendee>> call, @NonNull Response<List<Attendee>> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        allAttendees = response.body(); // Liste aktualisieren
                        if (onFinished != null) {
                            // Führe die nachfolgende Aktion aus (z.B. den Scan starten)
                            onFinished.run();
                        } else {
                            // Wenn kein Callback da ist (z.B. nach Check-in), nur Toast anzeigen
                            String message = allAttendees.size() + " Teilnehmer-Daten aktualisiert.";
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                        }
                        Log.i("APILOAD", allAttendees.size() + " Teilnehmer geladen.");
                    } else {
                        String errorMessage = "Fehler beim Laden der Teilnehmerliste: " + response.code();
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        Log.e("APILOAD", errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<List<Attendee>> call, @NonNull Throwable t) {
                runOnUiThread(() -> {
                    String failureMessage = "Netzwerkfehler beim Laden der Teilnehmer.";
                    Toast.makeText(getApplicationContext(), failureMessage, Toast.LENGTH_LONG).show();
                    Log.e("APILOAD", "Netzwerkfehler: " + t.getMessage(), t);
                });
            }
        });
    }

    private void searchAttendeeLocally(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer gefunden.", Toast.LENGTH_SHORT).show();
            return;
        }

        Attendee foundAttendee = null;
        for (Attendee attendee : allAttendees) {
            if (orderNumber.equals(attendee.getOrderNumber())) {
                foundAttendee = attendee;
                break;
            }
        }

        if (foundAttendee != null) {
            currentScannedAttendee = foundAttendee;

            attendeeNameText.setText("Teilnehmer: " + currentScannedAttendee.getParticipant());
            attendeeSeatsText.setText("Plätze: " + currentScannedAttendee.getGuestCount() +
                    " / Eingecheckt: " + currentScannedAttendee.getCheckedInCount());

            confirmationLayout.setVisibility(View.VISIBLE);
            textResult.setText("Bitte bestätigen Sie den Check-in.");
        } else {
            textResult.setText("Fehler: Bestellnummer '" + orderNumber + "' nicht in der Liste gefunden.");
            confirmationLayout.setVisibility(View.GONE);
        }
    }


    private void performCheckIn(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("APICHECKIN", "Starte Check-in für Bestellnummer: " + orderNumber);
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        CheckInRequest checkInRequest = new CheckInRequest(1);
        Call<Attendee> call = apiService.unCheck(orderNumber, checkInRequest);

        call.enqueue(new Callback<Attendee>() {
            @Override
            public void onResponse(@NonNull Call<Attendee> call, @NonNull Response<Attendee> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        Attendee checkedInAttendee = response.body();
                        String successMessage = "Check-in für " + checkedInAttendee.getParticipant() + " erfolgreich!";
                        Log.i("APICHECKIN", successMessage + " (Neue Anzahl: " + checkedInAttendee.getCheckedInCount() + ")");
                        Toast.makeText(MainActivity.this, successMessage, Toast.LENGTH_LONG).show();

                        textResult.setText(successMessage + "\nEingecheckt: " + checkedInAttendee.getCheckedInCount() + "/" + checkedInAttendee.getGuestCount());
                        // Lade die Teilnehmerliste neu, um den `checked_in_count` für zukünftige Scans zu aktualisieren
                        loadAllAttendees(null); // Aufruf ohne Callback
                    } else {
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
                runOnUiThread(() -> {
                    String failureMessage = "Netzwerkfehler: " + t.getMessage();
                    Log.e("APICHECKIN", failureMessage, t);
                    Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                    textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                });
            }
        });
    }


    private void performCheckInByName(String participantName) {
        if (participantName == null || participantName.trim().isEmpty()) {
            Toast.makeText(this, "Kein Name für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("APICHECKIN", "Starte Check-in für Teilnehmer: " + participantName);
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Attendee requestBody = new Attendee(participantName, null, 0, null);
        Call<Attendee> call = apiService.checkInByName(requestBody);

        call.enqueue(new Callback<Attendee>() {
            @Override
            public void onResponse(@NonNull Call<Attendee> call, @NonNull Response<Attendee> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        Attendee checkedInAttendee = response.body();
                        String successMessage = "Check-in für " + checkedInAttendee.getParticipant() + " erfolgreich!";
                        Log.i("APICHECKIN", successMessage + " (Neue Anzahl: " + checkedInAttendee.getCheckedInCount() + ")");
                        Toast.makeText(MainActivity.this, successMessage, Toast.LENGTH_LONG).show();
                        textResult.setText(successMessage + "\nEingecheckt: " + checkedInAttendee.getCheckedInCount() + "/" + checkedInAttendee.getGuestCount());
                        // Lade die Teilnehmerliste neu, um den `checked_in_count` für zukünftige Scans zu aktualisieren
                        loadAllAttendees(null); // Aufruf ohne Callback
                    } else {
                        String errorMessage;
                        if (response.code() == 404) {
                            errorMessage = "Fehler: Teilnehmer '" + participantName + "' nicht gefunden.";
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
