package com.example.da_prototyp_ocr;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    /**
     * Erstellt einen neuen Teilnehmer.
     * @param attendee Das Teilnehmer-Objekt, das als JSON-Body gesendet wird.
     */
    @POST("api/attendees")
    Call<AttendeeIdResponse> createAttendee(@Body Attendee attendee);

    /**
     * Ruft eine Liste von Teilnehmern ab, optional gefiltert nach einem Suchbegriff.
     * @param query Der Suchbegriff (kann null oder leer sein).
     */
    @GET("api/attendees")
    Call<List<Attendee>> getAttendees(@Query("query") String query);

    /**
     * Checkt eine bestimmte Anzahl von Gästen für eine Bestellnummer ein.
     * @param orderNumber Die Bestellnummer aus der URL.
     * @param checkInRequest Der Request-Body, der die Anzahl enthält.
     */
    @POST("api/checkin/{order}")
    Call<Attendee> checkIn(@Path("order") String orderNumber, @Body CheckInRequest checkInRequest);

    /**
     * Checkt eine bestimmte Anzahl von Gästen für eine Bestellnummer aus.
     * @param orderNumber Die Bestellnummer aus der URL.
     * @param checkInRequest Der Request-Body, der die Anzahl enthält.
     */
    @POST("api/uncheck/{order}")
    Call<Attendee> unCheck(@Path("order") String orderNumber, @Body CheckInRequest checkInRequest);

    /**
     * Checkt einen Teilnehmer anhand seines Namens ein.
     * @param attendee Das Teilnehmer-Objekt, das mindestens den Namen enthalten muss.
     */
    @POST("api/checkin-by-name")
    Call<Attendee> checkInByName(@Body Attendee attendee);
}
