package com.example.da_prototyp_ocr.dto;

import java.util.List;

/**
 * Request-Body für den Massenimport von Buchungen aus einer PDF.
 * Wird an POST /api/veranstaltungen/{id}/buchungen/import geschickt.
 */
public class ImportBuchungenRequest {

    public List<ImportBuchungItem> buchungen;  // Liste aller zu importierenden Teilnehmer

    public ImportBuchungenRequest(List<ImportBuchungItem> buchungen) {
        this.buchungen = buchungen;
    }
}