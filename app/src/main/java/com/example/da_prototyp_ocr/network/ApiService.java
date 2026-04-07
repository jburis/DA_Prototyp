package com.example.da_prototyp_ocr.network;

import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.example.da_prototyp_ocr.model.Buchung;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.dto.CheckinByBestellnummerRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameResponse;
import com.example.da_prototyp_ocr.dto.ImportBuchungenRequest;
import com.example.da_prototyp_ocr.dto.ImportBuchungenResponse;
import com.example.da_prototyp_ocr.dto.VeranstaltungCreateRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit Interface für alle Backend-API Endpunkte.
 * Retrofit generiert die Implementation automatisch basierend auf den Annotationen.
 *
 * Authentifizierung: Schreibende Operationen brauchen den x-admin-token Header.
 */
public interface ApiService {

    // ==================== VERANSTALTUNGEN ====================

    /**
     * Holt alle Veranstaltungen (für die Übersichtsliste).
     * GET /api/veranstaltungen
     */
    @GET("api/veranstaltungen")
    Call<List<Veranstaltung>> getVeranstaltungen();

    /**
     * Erstellt eine neue Veranstaltung (beim PDF-Import).
     * POST /api/veranstaltungen
     */
    @POST("api/veranstaltungen")
    Call<Veranstaltung> createVeranstaltung(
            @Header("x-admin-token") String adminToken,
            @Body VeranstaltungCreateRequest body
    );

    // ==================== BUCHUNGEN ====================

    /**
     * Holt alle Buchungen für eine Veranstaltung (Teilnehmerliste).
     * GET /api/buchungen/veranstaltung/{id}
     */
    @GET("api/buchungen/veranstaltung/{veranstaltung_id}")
    Call<List<Buchung>> getBuchungenByVeranstaltung(
            @Path("veranstaltung_id") int veranstaltungId
    );

    /**
     * Erstellt eine einzelne Buchung (manuelles Hinzufügen).
     * POST /api/buchungen
     */
    @POST("api/buchungen")
    Call<Buchung> createBuchung(
            @Header("x-admin-token") String adminToken,
            @Body Buchung buchung
    );

    /**
     * Löscht eine Buchung.
     * DELETE /api/buchungen/{id}
     */
    @DELETE("api/buchungen/{buchung_id}")
    Call<Void> deleteBuchung(
            @Path("buchung_id") int buchungId
    );

    /**
     * Massenimport von Buchungen aus PDF.
     * POST /api/veranstaltungen/{id}/buchungen/import
     */
    @POST("api/veranstaltungen/{veranstaltung_id}/buchungen/import")
    Call<ImportBuchungenResponse> importBuchungen(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body ImportBuchungenRequest body
    );

    // ==================== ANWESENHEITEN / CHECK-IN ====================

    /**
     * Holt alle Check-Ins für eine Veranstaltung.
     * Wird verwendet um checkedInCount pro Buchung zu berechnen.
     * GET /api/anwesenheiten/veranstaltung/{id}
     */
    @GET("api/anwesenheiten/veranstaltung/{veranstaltung_id}")
    Call<List<Anwesenheit>> getAnwesenheitenByVeranstaltung(
            @Path("veranstaltung_id") int veranstaltungId
    );

    /**
     * Check-In per Bestellnummer (OCR-Weg).
     * POST /api/anwesenheiten/veranstaltung/{id}/checkin/bestellnummer
     */
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/bestellnummer")
    Call<Anwesenheit> checkInByBestellnummer(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByBestellnummerRequest body
    );

    /**
     * Check-In per Name (QR-Code-Weg).
     * POST /api/anwesenheiten/veranstaltung/{id}/checkin/name
     */
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/name")
    Call<CheckinByNameResponse> checkInByName(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByNameRequest body
    );
}