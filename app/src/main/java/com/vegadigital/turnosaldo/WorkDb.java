package com.vegadigital.turnosaldo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class WorkDb extends SQLiteOpenHelper {
    public WorkDb(Context context) { super(context, "turnosaldo.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shifts (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "work_date TEXT NOT NULL, start_minutes INTEGER NOT NULL, " +
                "end_minutes INTEGER NOT NULL, break_minutes INTEGER NOT NULL, " +
                "hourly_rate REAL NOT NULL, currency_code TEXT NOT NULL DEFAULT 'PLN', " +
                "note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX shifts_date_idx ON shifts(work_date)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Los turnos guardados en V1 se registraron en złoty; NO se borran ni convierten.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE shifts ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'PLN'");
        }
    }

    private ContentValues values(WorkEntry item) {
        ContentValues v = new ContentValues();
        v.put("work_date", item.date.toString());
        v.put("start_minutes", item.startMinutes);
        v.put("end_minutes", item.endMinutes);
        v.put("break_minutes", item.breakMinutes);
        v.put("hourly_rate", item.hourlyRate);
        v.put("currency_code", item.currencyCode);
        v.put("note", item.note);
        return v;
    }

    public void save(WorkEntry item) {
        if (item.id == 0) {
            item.id = getWritableDatabase().insertOrThrow("shifts", null, values(item));
        } else {
            getWritableDatabase().update("shifts", values(item), "id=?",
                    new String[]{String.valueOf(item.id)});
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("shifts", "id=?", new String[]{String.valueOf(id)});
    }

    private WorkEntry from(Cursor c) {
        return new WorkEntry(
                c.getLong(c.getColumnIndexOrThrow("id")),
                LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))),
                c.getInt(c.getColumnIndexOrThrow("start_minutes")),
                c.getInt(c.getColumnIndexOrThrow("end_minutes")),
                c.getInt(c.getColumnIndexOrThrow("break_minutes")),
                c.getDouble(c.getColumnIndexOrThrow("hourly_rate")),
                c.getString(c.getColumnIndexOrThrow("currency_code")),
                c.getString(c.getColumnIndexOrThrow("note")));
    }

    public WorkEntry byId(long id) {
        try (Cursor c = getReadableDatabase().query("shifts", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? from(c) : null;
        }
    }

    public List<WorkEntry> forRange(LocalDate inclusive, LocalDate exclusive) {
        List<WorkEntry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("shifts", null,
                "work_date >= ? AND work_date < ?",
                new String[]{inclusive.toString(), exclusive.toString()},
                null, null, "work_date ASC, start_minutes ASC, id ASC")) {
            while (c.moveToNext()) out.add(from(c));
        }
        return out;
    }

    public List<WorkEntry> forMonth(YearMonth month) {
        return forRange(month.atDay(1), month.plusMonths(1).atDay(1));
    }
}
