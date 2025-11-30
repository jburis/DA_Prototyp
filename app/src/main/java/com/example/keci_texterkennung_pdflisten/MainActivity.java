package com.example.keci_texterkennung_pdflisten;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_PDF = 1001;

    private Button btnSelectPdf;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        tvResult = findViewById(R.id.tvResult);

        btnSelectPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPdfPicker();
            }
        });
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "PDF auswählen"), REQUEST_CODE_PICK_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_PDF && resultCode == RESULT_OK && data != null) {
            Uri pdfUri = data.getData();
            if (pdfUri != null) {
                tvResult.setText("PDF geladen, verarbeite...");
                extractTextFromFirstPage(pdfUri);
            }
        }
    }

    /**
     * Liest die erste Seite der PDF, rendert sie als Bitmap und startet ML Kit Text Recognition.
     * (Für den Anfang reicht Seite 1 – später können wir alle Seiten durchgehen.)
     */
    private void extractTextFromFirstPage(Uri pdfUri) {
        try {
            ParcelFileDescriptor pfd =
                    getContentResolver().openFileDescriptor(pdfUri, "r");
            if (pfd == null) {
                tvResult.setText("Fehler: Konnte PDF nicht öffnen.");
                return;
            }

            PdfRenderer renderer = new PdfRenderer(pfd);
            int targetPage = 0;
            if (renderer.getPageCount() > 1) {
                targetPage = 1; // Test: zweite Seite (Index 1)
            }
            PdfRenderer.Page page = renderer.openPage(targetPage);
            if (renderer.getPageCount() == 0) {
                tvResult.setText("PDF hat keine Seiten.");
                renderer.close();
                pfd.close();
                return;
            }


            Bitmap bitmap = Bitmap.createBitmap(
                    page.getWidth(),
                    page.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            renderer.close();
            pfd.close();

            runTextRecognition(bitmap);

        } catch (IOException e) {
            e.printStackTrace();
            tvResult.setText("Fehler beim Lesen der PDF: " + e.getMessage());
        }
    }

    /**
     * Übergibt das gerenderte PDF-Bitmap an ML Kit und zeigt den erkannten Text im TextView an.
     */
    private void runTextRecognition(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        com.google.mlkit.vision.text.TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        String raw = text.getText();
                        String info = "Zeichen gesamt: " + raw.length() + "\n\n" + raw;
                        tvResult.setText(info);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        tvResult.setText("Fehler bei der Texterkennung: " + e.getMessage());
                    }
                });
    }
}
