package com.example.da_prototyp_ocr.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.model.Buchung;

import java.util.List;

/**
 * Adapter für die Teilnehmerliste im Check-In Screen.
 * Zeigt Name, Bestellnummer und Check-In Status mit Farbindikator.
 *
 * Farben:
 * - Grau = noch niemand eingecheckt
 * - Orange = teilweise eingecheckt
 * - Grün = alle eingecheckt
 */
public class ParticipantAdapter extends BaseAdapter {

    private final Context context;
    private final List<Buchung> bookings;

    public ParticipantAdapter(Context context, List<Buchung> bookings) {
        this.context = context;
        this.bookings = bookings;
    }

    @Override
    public int getCount() {
        return bookings.size();
    }

    @Override
    public Object getItem(int position) {
        return bookings.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        // ViewHolder-Pattern für bessere Performance (Views werden recycled)
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_participant, parent, false);
            holder = new ViewHolder();
            holder.nameText = convertView.findViewById(R.id.nameText);
            holder.orderNumberText = convertView.findViewById(R.id.orderNumberText);
            holder.statusText = convertView.findViewById(R.id.statusText);
            holder.statusIndicator = convertView.findViewById(R.id.statusIndicator);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Buchung booking = bookings.get(position);

        // Name anzeigen (Fallback auf Vor+Nachname falls DisplayName leer)
        String displayName = booking.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = booking.getVorname() + " " + booking.getNachname();
        }
        holder.nameText.setText(displayName);

        // Bestellnummer
        holder.orderNumberText.setText(booking.getBestellnummer() != null ? booking.getBestellnummer() : "");

        // Status: "2/5 checked in"
        int checkedIn = booking.getCheckedInCount();
        int total = booking.getAnzahlPlaetze();
        holder.statusText.setText(checkedIn + "/" + total + " checked in");

        // Farbindikator je nach Status
        int color;
        if (checkedIn == 0) {
            color = Color.parseColor("#9E9E9E");  // Grau – noch niemand da
        } else if (checkedIn < total) {
            color = Color.parseColor("#FF9800");  // Orange – teilweise da
        } else {
            color = Color.parseColor("#4CAF50");  // Grün – alle da
        }
        holder.statusIndicator.setBackgroundColor(color);
        holder.statusText.setTextColor(color);

        return convertView;
    }

    /**
     * ViewHolder für Performance-Optimierung.
     * Verhindert wiederholte findViewById()-Aufrufe beim Scrollen.
     */
    static class ViewHolder {
        TextView nameText;
        TextView orderNumberText;
        TextView statusText;
        View statusIndicator;
    }
}