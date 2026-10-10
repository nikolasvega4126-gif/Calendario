package com.vegadigital.turnosaldo;

import java.time.LocalDate;
import java.util.Locale;

/** Registro persistente. Los importes conservan su divisa original. */
public final class WorkEntry {
    public long id;
    public LocalDate date;
    public int startMinutes;
    public int endMinutes;
    public int breakMinutes;
    public double hourlyRate;
    public String currencyCode;
    public String note;

    public WorkEntry(long id, LocalDate date, int startMinutes, int endMinutes,
                     int breakMinutes, double hourlyRate, String currencyCode, String note) {
        this.id = id;
        this.date = date;
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.breakMinutes = breakMinutes;
        this.hourlyRate = hourlyRate;
        this.currencyCode = currencyCode == null ? "PLN" : currencyCode;
        this.note = note == null ? "" : note;
    }

    // Compatibilidad con cálculos y pruebas del proyecto V1.
    public WorkEntry(long id, LocalDate date, int startMinutes, int endMinutes,
                     int breakMinutes, double hourlyRate, String note) {
        this(id, date, startMinutes, endMinutes, breakMinutes, hourlyRate, "PLN", note);
    }

    public int grossMinutes() {
        int total = endMinutes - startMinutes;
        if (total < 0) total += 24 * 60;
        return total;
    }

    public int workedMinutes() { return Math.max(0, grossMinutes() - breakMinutes); }
    public double earnings() { return workedMinutes() * hourlyRate / 60.0; }
    public static String hhmm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
