package com.vegadigital.turnosaldo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** TurnoSaldo 2.0. UI nativa sin dependencias externas y con soporte de monedas ISO 4217. */
public class MainActivity extends Activity {
    private static final int HOME = 0, CALENDAR = 1, REPORTS = 2, HISTORY = 3, SETTINGS = 4, DETAIL = 5;
    private static final int EXPORT_REQUEST = 720;
    private static final int NAVY = 0xFF10233E;
    private static final int BLUE = 0xFF1877ED;
    private static final int STRONG_BLUE = 0xFF0C63CF;
    private static final int GREEN = 0xFF09894E;
    private static final int TURQUOISE = 0xFF3DC9B8;
    private static final int BG = 0xFFF3F7FD;
    private static final int MUTED = 0xFF6F7D94;
    private static final int LIGHT_BLUE = 0xFFE5F1FF;
    private static final int LIGHT_GREEN = 0xFFDCF7E9;
    private static final int DIVIDER = 0xFFEDF0F5;
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    private static final String[] DAYS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private WorkDb db;
    private SharedPreferences prefs;
    private YearMonth viewedMonth;
    private LocalDate selectedDay;
    private YearMonth pendingExportMonth;
    private String pendingExportCurrency;
    private int screen = HOME;
    private long detailId = -1L;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new WorkDb(this);
        prefs = getSharedPreferences("turnosaldo_settings", MODE_PRIVATE);
        viewedMonth = YearMonth.now();
        selectedDay = LocalDate.now();
        // Quien viene de V1 conservaba sus jornadas y tarifa en PLN.
        if (!prefs.contains("currency_code")) {
            String first = prefs.contains("hourly_rate") ? "PLN" : CurrencyTools.localCurrency();
            prefs.edit().putString("currency_code", first).apply();
        }
        if (state != null) {
            screen = state.getInt("screen", HOME);
            viewedMonth = YearMonth.parse(state.getString("month", viewedMonth.toString()));
            selectedDay = LocalDate.parse(state.getString("day", selectedDay.toString()));
            detailId = state.getLong("detailId", -1L);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().setStatusBarColor(STRONG_BLUE);
            getWindow().setNavigationBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("screen", screen);
        out.putString("month", viewedMonth.toString());
        out.putString("day", selectedDay.toString());
        out.putLong("detailId", detailId);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (screen == DETAIL) { screen = CALENDAR; render(); }
        else if (screen != HOME) { screen = HOME; render(); }
        else super.onBackPressed();
    }

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable outline(int fill, int stroke, int radius) {
        GradientDrawable d = shape(fill, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setIncludeFontPadding(true);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return v;
    }

    private LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    private void line(LinearLayout parent, int top) {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(top);
        parent.addView(v, lp);
    }

    private LinearLayout card(int fill, int padding) {
        LinearLayout c = col();
        c.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        c.setBackground(shape(fill, 20));
        if (fill == Color.WHITE) c.setElevation(dp(2));
        return c;
    }

    private TextView pill(String label, int fill, int ink) {
        TextView v = text(label, 14, ink, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(13), dp(10), dp(13), dp(10));
        v.setBackground(shape(fill, 12));
        return v;
    }

    private String currency() { return prefs.getString("currency_code", "USD"); }

    private double rateFor(String currencyCode) {
        if (prefs.contains("rate_" + currencyCode)) return prefs.getFloat("rate_" + currencyCode, 0f);
        // Tarifa histórica del proyecto V1 exclusivamente en PLN.
        return "PLN".equals(currencyCode) ? prefs.getFloat("hourly_rate", 0f) : 0;
    }

    private String money(double amount) { return CurrencyTools.money(amount, currency()); }
    private String money(WorkEntry e) { return CurrencyTools.money(e.earnings(), e.currencyCode); }
    private String money(double amount, String code) { return CurrencyTools.money(amount, code); }

    private String hours(int minutes) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(ES))
                .format(minutes / 60.0) + " h";
    }

    private String niceDate(LocalDate date) {
        String v = date.format(DAY_LONG);
        return v.substring(0, 1).toUpperCase(ES) + v.substring(1);
    }

    private void go(int next) { screen = next; render(); }

    private List<WorkEntry> selectedCurrency(List<WorkEntry> raw) {
        List<WorkEntry> list = new ArrayList<>();
        String code = currency();
        for (WorkEntry entry : raw) if (code.equals(entry.currencyCode)) list.add(entry);
        return list;
    }

    private List<WorkEntry> monthEntries() { return selectedCurrency(db.forMonth(viewedMonth)); }

    private List<WorkEntry> weekEntries() {
        LocalDate monday = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return selectedCurrency(db.forRange(monday, monday.plusDays(7)));
    }

    private void render() {
        LinearLayout root = col();
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.addView(header());
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = col();
        content.setPadding(dp(15), dp(14), dp(15), dp(26));
        switch (screen) {
            case CALENDAR: drawCalendar(content); break;
            case REPORTS: drawReports(content); break;
            case HISTORY: drawHistory(content); break;
            case SETTINGS: drawSettings(content); break;
            case DETAIL: drawDetail(content); break;
            default: drawHome(content);
        }
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (screen != DETAIL) root.addView(bottomNav());
        setContentView(root);
        root.requestApplyInsets();
    }

    private View header() {
        if (screen == DETAIL) {
            LinearLayout h = row();
            h.setBackgroundColor(Color.WHITE);
            h.setPadding(dp(18), dp(15), dp(18), dp(14));
            IconView back = new IconView("back", NAVY);
            h.addView(back, new LinearLayout.LayoutParams(dp(32), dp(32)));
            back.setOnClickListener(v -> go(CALENDAR));
            TextView title = text("Detalle del turno", 20, NAVY, true);
            title.setGravity(Gravity.CENTER);
            h.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            h.addView(new View(this), new LinearLayout.LayoutParams(dp(32), dp(32)));
            return h;
        }
        LinearLayout h = row();
        h.setPadding(dp(21), dp(24), dp(19), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF1163C3, 0xFF218FC9, 0xFF13B88C});
        h.setBackground(bg);
        LinearLayout iconBox = col();
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(outline(0x33FFFFFF, 0x88FFFFFF, 14));
        iconBox.addView(new IconView("calendar", Color.WHITE),
                new LinearLayout.LayoutParams(dp(35), dp(35)));
        h.addView(iconBox, new LinearLayout.LayoutParams(dp(51), dp(51)));
        LinearLayout headerText = col();
        headerText.setPadding(dp(12), 0, 0, 0);
        TextView headerTitle = text("Calendario Laboral", 23, Color.WHITE, true);
        headerTitle.setSingleLine(true);
        headerTitle.setAutoSizeTextTypeUniformWithConfiguration(17, 23, 1, TypedValue.COMPLEX_UNIT_SP);
        headerText.addView(headerTitle);
        TextView caption = text("Organiza tu tiempo, aumenta tus ganancias", 11, Color.WHITE, false);
        caption.setSingleLine(false);
        headerText.addView(caption);
        h.addView(headerText, new LinearLayout.LayoutParams(0, -2, 1));
        return h;
    }

    private View bottomNav() {
        LinearLayout nav = row();
        nav.setPadding(dp(3), dp(8), dp(3), dp(9));
        nav.setBackgroundColor(Color.WHITE);
        String[] titles = {"Inicio", "Calendario", "Reportes", "Historial", "Ajustes"};
        String[] symbols = {"home", "calendar", "bars", "history", "settings"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            int color = (screen == i) ? BLUE : MUTED;
            LinearLayout item = col();
            item.setGravity(Gravity.CENTER);
            item.addView(new IconView(symbols[i], color),
                    new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView caption = text(titles[i], 10, color, screen == i);
            caption.setGravity(Gravity.CENTER);
            add(item, caption, 3);
            item.setOnClickListener(v -> go(index));
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(51), 1));
        }
        return nav;
    }

    private LinearLayout calendarPanel() {
        LinearLayout cal = card(Color.WHITE, 12);
        LinearLayout monthBar = row();
        IconView left = new IconView("back", NAVY);
        left.setOnClickListener(v -> changeMonth(-1));
        monthBar.addView(left, new LinearLayout.LayoutParams(dp(35), dp(35)));
        String month = viewedMonth.getMonth().getDisplayName(TextStyle.FULL, ES);
        month = month.substring(0, 1).toUpperCase(ES) + month.substring(1);
        TextView center = text(month + " " + viewedMonth.getYear(), 18, NAVY, true);
        center.setGravity(Gravity.CENTER);
        monthBar.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
        IconView right = new IconView("next", NAVY);
        right.setOnClickListener(v -> changeMonth(1));
        monthBar.addView(right, new LinearLayout.LayoutParams(dp(35), dp(35)));
        cal.addView(monthBar);

        LinearLayout labels = row();
        for (String day : DAYS) {
            TextView tv = text(day, 12, MUTED, false);
            tv.setGravity(Gravity.CENTER);
            labels.addView(tv, new LinearLayout.LayoutParams(0, dp(34), 1));
        }
        add(cal, labels, 6);
        Set<LocalDate> marked = new HashSet<>();
        for (WorkEntry shift : monthEntries()) marked.add(shift.date);
        int leading = viewedMonth.atDay(1).getDayOfWeek().getValue() - 1;
        int slots = ((leading + viewedMonth.lengthOfMonth() + 6) / 7) * 7;
        for (int start = 0; start < slots; start += 7) {
            LinearLayout week = row();
            for (int j = 0; j < 7; j++) {
                int number = start + j - leading + 1;
                LinearLayout slot = col();
                slot.setGravity(Gravity.CENTER);
                if (number >= 1 && number <= viewedMonth.lengthOfMonth()) {
                    LocalDate date = viewedMonth.atDay(number);
                    boolean chosen = date.equals(selectedDay);
                    boolean hasWork = marked.contains(date);
                    LinearLayout circle = col();
                    circle.setGravity(Gravity.CENTER);
                    circle.setBackground(shape(chosen ? BLUE : hasWork ? LIGHT_GREEN : Color.WHITE, 40));
                    TextView dayText = text(String.valueOf(number), 15,
                            chosen ? Color.WHITE : NAVY, chosen);
                    dayText.setGravity(Gravity.CENTER);
                    circle.addView(dayText);
                    if (hasWork) {
                        View dot = new View(this);
                        dot.setBackground(shape(chosen ? Color.WHITE : GREEN, 8));
                        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(4), dp(4));
                        dl.topMargin = dp(1);
                        circle.addView(dot, dl);
                    }
                    slot.addView(circle, new LinearLayout.LayoutParams(dp(38), dp(38)));
                    slot.setOnClickListener(v -> { selectedDay = date; render(); });
                }
                week.addView(slot, new LinearLayout.LayoutParams(0, dp(43), 1));
            }
            cal.addView(week);
        }
        return cal;
    }

    private void changeMonth(int by) {
        viewedMonth = viewedMonth.plusMonths(by);
        selectedDay = viewedMonth.atDay(1);
        render();
    }

    private LinearLayout metric(String glyph, String label, String value, String foot, boolean green) {
        LinearLayout c = card(Color.WHITE, 12);
        LinearLayout top = row();
        LinearLayout ico = col();
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(shape(green ? LIGHT_GREEN : LIGHT_BLUE, 14));
        ico.addView(new IconView(glyph, green ? GREEN : BLUE),
                new LinearLayout.LayoutParams(dp(27), dp(27)));
        top.addView(ico, new LinearLayout.LayoutParams(dp(47), dp(47)));
        LinearLayout captions = col();
        captions.setPadding(dp(9), 0, 0, 0);
        TextView small = text(label, 11, MUTED, false);
        captions.addView(small);
        TextView big = text(value, 20, green ? GREEN : NAVY, true);
        big.setMaxLines(1);
        big.setAutoSizeTextTypeUniformWithConfiguration(12, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        captions.addView(big);
        top.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(top);
        TextView bottom = text(foot, 11, MUTED, false);
        bottom.setGravity(Gravity.END);
        add(c, bottom, 5);
        return c;
    }

    private void twoMetrics(LinearLayout content, View a, View b, int top) {
        LinearLayout r = row();
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.rightMargin = dp(5);
        r.addView(a, first);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.leftMargin = dp(5);
        r.addView(b, second);
        add(content, r, top);
    }

    private void drawHome(LinearLayout content) {
        add(content, calendarPanel(), 0);
        if (rateFor(currency()) == 0 && monthEntries().isEmpty()) {
            TextView setup = pill("Configura tu tarifa por hora · " + currency() + "  ›", LIGHT_BLUE, STRONG_BLUE);
            setup.setOnClickListener(v -> go(SETTINGS));
            add(content, setup, 11);
        }
        List<WorkEntry> m = monthEntries();
        List<WorkEntry> w = weekEntries();
        int dayMinutes = 0, monthMinutes = 0, weekMinutes = 0;
        double dayPay = 0, monthPay = 0, weekPay = 0;
        for (WorkEntry e : m) {
            monthMinutes += e.workedMinutes(); monthPay += e.earnings();
            if (e.date.equals(selectedDay)) {
                dayMinutes += e.workedMinutes(); dayPay += e.earnings();
            }
        }
        for (WorkEntry e : w) { weekMinutes += e.workedMinutes(); weekPay += e.earnings(); }
        twoMetrics(content,
                metric("clock", "Horas del día", hours(dayMinutes), "Fecha seleccionada", false),
                metric("coins", "Ganancias del día", money(dayPay), currency(), true), 12);
        twoMetrics(content,
                metric("bars", "Esta semana", money(weekPay), hours(weekMinutes), false),
                metric("wallet", "Este mes", money(monthPay), hours(monthMinutes), true), 10);

        LinearLayout actions = row();
        String[] names = {"Agregar turno", "Ver reporte", "Historial"};
        String[] icons = {"plus", "bars", "history"};
        int[] fills = {BLUE, LIGHT_BLUE, LIGHT_GREEN};
        int[] colors = {Color.WHITE, STRONG_BLUE, GREEN};
        int[] target = {CALENDAR, REPORTS, HISTORY};
        for (int i = 0; i < names.length; i++) {
            final int tab = target[i];
            LinearLayout button = col();
            button.setGravity(Gravity.CENTER);
            button.setBackground(shape(fills[i], 16));
            button.addView(new IconView(icons[i], colors[i]),
                    new LinearLayout.LayoutParams(dp(25), dp(25)));
            TextView label = text(names[i], 12, colors[i], true);
            label.setGravity(Gravity.CENTER);
            add(button, label, 7);
            button.setOnClickListener(v -> {
                if (tab == CALENDAR) showEntryDialog(null);
                else go(tab);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(83), 1);
            lp.leftMargin = dp(i == 0 ? 0 : 4);
            lp.rightMargin = dp(i == 2 ? 0 : 4);
            actions.addView(button, lp);
        }
        add(content, actions, 12);
        add(content, weeklyChartCard(), 13);
        dayList(content, false);
    }

    private LinearLayout weeklyChartCard() {
        LinearLayout c = card(Color.WHITE, 14);
        LinearLayout heading = row();
        TextView left = text("Ingresos semanales", 16, NAVY, true);
        heading.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        double[] barValues = new double[7];
        LocalDate monday = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (WorkEntry e : weekEntries()) {
            int i = e.date.getDayOfWeek().getValue() - 1;
            barValues[i] += e.earnings();
        }
        double sum = 0;
        for (double value : barValues) sum += value;
        TextView right = text(money(sum), 13, NAVY, true);
        heading.addView(right);
        c.addView(heading);
        TextView when = text("Semana del " + monday.format(DAY_SHORT) + " · " + currency(), 11, MUTED, false);
        add(c, when, 3);
        IncomeChart chart = new IncomeChart(barValues);
        add(c, chart, 12);
        return c;
    }

    private void drawCalendar(LinearLayout content) {
        add(content, text("Mi calendario de trabajo", 22, NAVY, true), 2);
        add(content, text("Toca un día para ver o registrar tus turnos.", 13, MUTED, false), 5);
        add(content, calendarPanel(), 17);
        dayList(content, true);
    }

    private void dayList(LinearLayout content, boolean large) {
        LinearLayout bar = row();
        LinearLayout titles = col();
        titles.addView(text("Turnos del día", 18, NAVY, true));
        add(titles, text(niceDate(selectedDay), 12, MUTED, false), 3);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView plus = pill("+ Agregar", BLUE, Color.WHITE);
        plus.setOnClickListener(v -> showEntryDialog(null));
        bar.addView(plus);
        add(content, bar, large ? 22 : 20);
        List<WorkEntry> list = selectedCurrency(db.forRange(selectedDay, selectedDay.plusDays(1)));
        if (list.isEmpty()) {
            LinearLayout no = card(Color.WHITE, 17);
            TextView empty = text("Sin turnos registrados en " + currency(), 14, NAVY, true);
            empty.setGravity(Gravity.CENTER);
            no.addView(empty);
            TextView tip = text("Añade un horario para calcular tus ganancias.", 12, MUTED, false);
            tip.setGravity(Gravity.CENTER);
            add(no, tip, 7);
            add(content, no, 12);
        } else {
            double total = 0; int minutes = 0;
            for (WorkEntry e : list) {
                total += e.earnings(); minutes += e.workedMinutes();
                add(content, shiftCard(e), 9);
            }
            LinearLayout summary = card(LIGHT_GREEN, 15);
            LinearLayout r = row();
            TextView title = text("Total del día · " + hours(minutes), 13, GREEN, true);
            r.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(text(money(total), 16, GREEN, true));
            summary.addView(r);
            add(content, summary, 11);
        }
    }

    private LinearLayout shiftCard(WorkEntry e) {
        LinearLayout c = card(Color.WHITE, 14);
        LinearLayout r = row();
        LinearLayout left = col();
        left.addView(text(WorkEntry.hhmm(e.startMinutes) + "  a  " + WorkEntry.hhmm(e.endMinutes) +
                (e.endMinutes < e.startMinutes ? "  (+1 día)" : ""), 16, NAVY, true));
        add(left, text(hours(e.workedMinutes()) + " · Descanso: " + e.breakMinutes + " min", 12, MUTED, false), 6);
        r.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(text(money(e), 14, GREEN, true));
        c.addView(r);
        if (!e.note.trim().isEmpty()) add(c, text(e.note, 12, MUTED, false), 9);
        add(c, text("Ver detalles ›", 12, BLUE, true), 9);
        c.setOnClickListener(v -> { detailId = e.id; go(DETAIL); });
        return c;
    }

    private void drawReports(LinearLayout content) {
        add(content, text("Reportes de ganancias", 22, NAVY, true), 3);
        add(content, text("Resumen en " + currency() + ". Las divisas no se mezclan.", 12, MUTED, false), 5);
        add(content, monthPickerCompact(), 17);
        List<WorkEntry> entries = monthEntries();
        double total = 0; int mins = 0;
        Set<LocalDate> days = new HashSet<>();
        TreeMap<LocalDate, Double> weeks = new TreeMap<>();
        for (WorkEntry e : entries) {
            total += e.earnings(); mins += e.workedMinutes(); days.add(e.date);
            LocalDate monday = e.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeks.merge(monday, e.earnings(), Double::sum);
        }
        LinearLayout earnings = card(STRONG_BLUE, 20);
        earnings.addView(text("GANANCIAS ESTE MES", 12, 0xFFDCEAFF, true));
        add(earnings, text(money(total), 31, Color.WHITE, true), 8);
        add(earnings, text("Ganancia bruta estimada, sin impuestos", 12, Color.WHITE, false), 5);
        add(content, earnings, 13);
        twoMetrics(content,
                metric("clock", "Horas trabajadas", hours(mins), entries.size() + " turnos", false),
                metric("calendar", "Días trabajados", String.valueOf(days.size()), "En " + viewedMonth, true), 12);
        twoMetrics(content,
                metric("coins", "Promedio por día", money(days.isEmpty() ? 0 : total / days.size()), "Por día registrado", true),
                metric("wallet", "Tarifa media", money(mins == 0 ? 0 : total / (mins / 60.0)), "Por hora", false), 9);
        LinearLayout weekCard = card(Color.WHITE, 16);
        weekCard.addView(text("Ingresos por semana", 17, NAVY, true));
        if (weeks.isEmpty()) add(weekCard, text("Sin datos para este mes.", 13, MUTED, false), 12);
        else {
            double max = 0;
            for (double n : weeks.values()) max = Math.max(max, n);
            for (Map.Entry<LocalDate, Double> w : weeks.entrySet()) {
                LinearLayout r = row();
                r.addView(text("Desde " + w.getKey().getDayOfMonth() + " " +
                        w.getKey().getMonth().getDisplayName(TextStyle.SHORT, ES), 12, MUTED, false),
                        new LinearLayout.LayoutParams(dp(90), -2));
                LinearLayout trackContainer = col();
                trackContainer.setBackground(shape(LIGHT_BLUE, 7));
                View progress = new View(this);
                progress.setBackground(shape(TURQUOISE, 7));
                // El ancho se establece proporcionalmente cuando se mida el contenedor.
                final double share = max == 0 ? 0 : w.getValue() / max;
                trackContainer.addView(progress, new LinearLayout.LayoutParams(0, dp(13)));
                trackContainer.post(() -> {
                    ViewGroup.LayoutParams lp = progress.getLayoutParams();
                    lp.width = Math.max(0, (int) (trackContainer.getWidth() * share));
                    progress.setLayoutParams(lp);
                });
                r.addView(trackContainer, new LinearLayout.LayoutParams(0, dp(13), 1));
                TextView amount = text(money(w.getValue()), 11, NAVY, true);
                amount.setGravity(Gravity.END);
                r.addView(amount, new LinearLayout.LayoutParams(dp(110), -2));
                add(weekCard, r, 14);
            }
        }
        add(content, weekCard, 17);
        TextView export = pill("↓  Exportar mes a CSV · " + currency(), BLUE, Color.WHITE);
        export.setOnClickListener(v -> exportCSV());
        add(content, export, 18);
        add(content, text("El archivo exporta solo los turnos en " + currency() +
                ". Los registros de otras monedas se mantienen intactos.", 12, MUTED, false), 9);
    }

    private View monthPickerCompact() {
        LinearLayout r = row();
        TextView previous = pill("‹", LIGHT_BLUE, BLUE);
        previous.setOnClickListener(v -> changeMonth(-1));
        r.addView(previous);
        String month = viewedMonth.getMonth().getDisplayName(TextStyle.FULL, ES);
        TextView label = text(month.substring(0, 1).toUpperCase(ES) + month.substring(1) + " " +
                viewedMonth.getYear(), 16, NAVY, true);
        label.setGravity(Gravity.CENTER);
        r.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView next = pill("›", LIGHT_BLUE, BLUE);
        next.setOnClickListener(v -> changeMonth(1));
        r.addView(next);
        return r;
    }

    private void drawHistory(LinearLayout content) {
        add(content, text("Historial de turnos", 22, NAVY, true), 2);
        add(content, text("Registro de " + currency() + " · selecciona un turno para editarlo.", 12, MUTED, false), 5);
        add(content, monthPickerCompact(), 19);
        List<WorkEntry> entries = monthEntries();
        if (entries.isEmpty()) {
            LinearLayout box = card(Color.WHITE, 20);
            box.addView(text("No hay turnos en esta moneda y este mes.", 14, MUTED, false));
            add(content, box, 18);
            return;
        }
        TreeMap<LocalDate, List<WorkEntry>> days = new TreeMap<>((a, b) -> b.compareTo(a));
        for (WorkEntry e : entries) days.computeIfAbsent(e.date, k -> new ArrayList<>()).add(e);
        for (Map.Entry<LocalDate, List<WorkEntry>> item : days.entrySet()) {
            add(content, text(niceDate(item.getKey()), 15, NAVY, true), 20);
            List<WorkEntry> shifts = item.getValue();
            for (int i = shifts.size() - 1; i >= 0; i--) add(content, shiftCard(shifts.get(i)), 8);
        }
    }

    private void drawSettings(LinearLayout content) {
        add(content, text("Ajustes", 22, NAVY, true), 2);
        add(content, text("Personaliza tu moneda y tarifa por hora.", 13, MUTED, false), 5);
        LinearLayout currencies = card(Color.WHITE, 18);
        currencies.addView(text("Moneda de trabajo", 17, NAVY, true));
        add(currencies, text("Elige entre las divisas ISO compatibles con tu Android.", 12, MUTED, false), 6);
        LinearLayout selector = row();
        selector.setPadding(dp(13), dp(12), dp(12), dp(12));
        selector.setBackground(outline(LIGHT_BLUE, 0xFFCADFFA, 13));
        LinearLayout s = col();
        s.addView(text(currency() + " · " + CurrencyTools.name(currency()), 15, STRONG_BLUE, true));
        add(s, text("Toca para cambiar moneda", 12, MUTED, false), 3);
        selector.addView(s, new LinearLayout.LayoutParams(0, -2, 1));
        selector.addView(new IconView("next", BLUE), new LinearLayout.LayoutParams(dp(22), dp(22)));
        selector.setOnClickListener(v -> currencyPicker(currency(), code -> {
            prefs.edit().putString("currency_code", code).apply();
            Toast.makeText(this, "Moneda seleccionada: " + code, Toast.LENGTH_SHORT).show();
            render();
        }));
        add(currencies, selector, 14);
        add(currencies, text("Cambiar de moneda no convierte valores ni modifica tus turnos anteriores.",
                12, MUTED, false), 12);
        add(content, currencies, 18);
        LinearLayout wages = card(Color.WHITE, 18);
        wages.addView(text("Tarifa por hora · " + currency(), 17, NAVY, true));
        add(wages, text("Se aplicará a los turnos nuevos de esta moneda.", 12, MUTED, false), 6);
        EditText input = numeric(String.valueOf(rateFor(currency())).replace('.', ','));
        input.setTextSize(23);
        input.setSelectAllOnFocus(true);
        add(wages, input, 11);
        TextView save = pill("Guardar tarifa", BLUE, Color.WHITE);
        save.setOnClickListener(v -> {
            try {
                double rate = parseRate(input.getText().toString());
                if (!Double.isFinite(rate) || rate < 0 || rate > 1000000000) throw new NumberFormatException();
                prefs.edit().putFloat("rate_" + currency(), (float) rate).apply();
                Toast.makeText(this, "Tarifa guardada para " + currency(), Toast.LENGTH_SHORT).show();
                render();
            } catch (NumberFormatException ex) { input.setError("Indica una tarifa válida"); }
        });
        add(wages, save, 15);
        add(content, wages, 13);
        LinearLayout help = card(Color.WHITE, 18);
        help.addView(text("Cómo funcionan los cálculos", 16, NAVY, true));
        add(help, text("(Minutos trabajados − descanso) × tarifa por hora. " +
                "Admite turnos nocturnos y múltiples jornadas al día.", 13, MUTED, false), 10);
        line(help, 14);
        add(help, text("Privacidad", 16, NAVY, true), 13);
        add(help, text("Los datos se guardan localmente. No necesitas crear cuenta ni tener internet. " +
                "Exporta tus meses antes de cambiar de teléfono o desinstalar.", 13, MUTED, false), 9);
        add(help, text("TurnoSaldo 2.0 · Vega Digital Technologies", 12, BLUE, true), 14);
        add(content, help, 13);
    }

    private void drawDetail(LinearLayout content) {
        WorkEntry e = db.byId(detailId);
        if (e == null) { go(CALENDAR); return; }
        LinearLayout heading = card(LIGHT_BLUE, 18);
        LinearLayout headingRow = row();
        headingRow.addView(new IconView("calendar", BLUE),
                new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout headingText = col();
        headingText.setPadding(dp(11), 0, 0, 0);
        headingText.addView(text(niceDate(e.date), 15, NAVY, true));
        TextView complete = pill("Turno completado", LIGHT_GREEN, GREEN);
        add(headingText, complete, 6);
        headingRow.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(headingRow);
        add(content, heading, 8);
        LinearLayout details = card(Color.WHITE, 18);
        detailLine(details, "clock", "Hora de entrada", WorkEntry.hhmm(e.startMinutes));
        line(details, 14);
        detailLine(details, "history", "Hora de salida", WorkEntry.hhmm(e.endMinutes) +
                (e.endMinutes < e.startMinutes ? " (+1 día)" : ""));
        line(details, 14);
        detailLine(details, "bars", "Total de horas", hours(e.workedMinutes()));
        line(details, 14);
        detailLine(details, "clock", "Descanso", e.breakMinutes + " min");
        line(details, 14);
        detailLine(details, "coins", "Pago por hora", money(e.hourlyRate, e.currencyCode) + "/h");
        line(details, 14);
        detailLine(details, "wallet", "Moneda", e.currencyCode);
        add(content, details, 13);
        LinearLayout earnings = card(LIGHT_GREEN, 17);
        earnings.addView(text("Ganancia total del día (este turno)", 13, NAVY, true));
        add(earnings, text(money(e), 30, GREEN, true), 6);
        add(content, earnings, 12);
        LinearLayout notes = card(Color.WHITE, 17);
        notes.addView(text("Notas", 15, NAVY, true));
        add(notes, text(e.note.trim().isEmpty() ? "Sin notas" : e.note, 13, MUTED, false), 10);
        add(content, notes, 13);
        TextView edit = pill("✎  Editar turno", LIGHT_BLUE, STRONG_BLUE);
        edit.setOnClickListener(v -> showEntryDialog(e));
        add(content, edit, 15);
        TextView remove = pill("Eliminar turno", 0xFFFFF2F3, 0xFFE03E45);
        remove.setBackground(outline(0xFFFFF2F3, 0xFFF2A5AA, 14));
        remove.setOnClickListener(v -> confirmDelete(e));
        add(content, remove, 10);
    }

    private void detailLine(LinearLayout panel, String icon, String title, String value) {
        LinearLayout item = row();
        item.addView(new IconView(icon, 0xFF647FA8), new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout pair = col();
        pair.setPadding(dp(12), 0, 0, 0);
        pair.addView(text(title, 12, MUTED, false));
        add(pair, text(value, 17, NAVY, true), 3);
        item.addView(pair);
        add(panel, item, 12);
    }

    private void confirmDelete(WorkEntry e) {
        new AlertDialog.Builder(this).setTitle("Eliminar turno")
                .setMessage("Esta acción eliminará el registro de forma permanente.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, id) -> {
                    db.delete(e.id);
                    viewedMonth = YearMonth.from(e.date);
                    selectedDay = e.date;
                    go(CALENDAR);
                }).show();
    }

    private interface CurrencyPicked { void onPicked(String code); }

    private void currencyPicker(String current, CurrencyPicked callback) {
        LinearLayout body = col();
        body.setPadding(dp(16), dp(10), dp(16), dp(8));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Buscar por código o nombre: EUR, peso...");
        search.setTextSize(15);
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        ListView choices = new ListView(this);
        List<String> labels = CurrencyTools.labels();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        choices.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, dp(390));
        listParams.topMargin = dp(9);
        body.addView(choices, listParams);
        AlertDialog picker = new AlertDialog.Builder(this)
                .setTitle("Selecciona una moneda · " + current)
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .create();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        choices.setOnItemClickListener((parent, view, position, id) -> {
            String label = adapter.getItem(position);
            if (label == null || label.length() < 3) return;
            String code = label.substring(0, 3);
            if (!CurrencyTools.valid(code)) return;
            picker.dismiss();
            callback.onPicked(code);
        });
        picker.show();
    }

    private static double parseRate(String value) {
        return Double.parseDouble(value.trim().replace(" ", "").replace(',', '.'));
    }

    private EditText numeric(String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private void showEntryDialog(WorkEntry existing) {
        final WorkEntry draft = existing == null ?
                new WorkEntry(0L, selectedDay, 8 * 60, 16 * 60, 0, rateFor(currency()), currency(), "") :
                new WorkEntry(existing.id, existing.date, existing.startMinutes, existing.endMinutes,
                        existing.breakMinutes, existing.hourlyRate, existing.currencyCode, existing.note);
        LinearLayout form = col();
        form.setPadding(dp(18), dp(7), dp(18), dp(8));
        TextView date = pill("Fecha: " + draft.date, LIGHT_BLUE, STRONG_BLUE);
        date.setOnClickListener(v -> new DatePickerDialog(this, (picker, y, mo, d) -> {
            draft.date = LocalDate.of(y, mo + 1, d);
            date.setText("Fecha: " + draft.date);
        }, draft.date.getYear(), draft.date.getMonthValue() - 1, draft.date.getDayOfMonth()).show());
        add(form, date, 4);
        add(form, text("Horario del turno", 14, NAVY, true), 17);
        LinearLayout clocks = row();
        TextView start = pill("Entrada  " + WorkEntry.hhmm(draft.startMinutes), LIGHT_BLUE, STRONG_BLUE);
        TextView end = pill("Salida  " + WorkEntry.hhmm(draft.endMinutes), LIGHT_BLUE, STRONG_BLUE);
        start.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            draft.startMinutes = h * 60 + m;
            start.setText("Entrada  " + WorkEntry.hhmm(draft.startMinutes));
        }, draft.startMinutes / 60, draft.startMinutes % 60, true).show());
        end.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            draft.endMinutes = h * 60 + m;
            end.setText("Salida  " + WorkEntry.hhmm(draft.endMinutes));
        }, draft.endMinutes / 60, draft.endMinutes % 60, true).show());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(5);
        clocks.addView(start, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
        right.leftMargin = dp(5);
        clocks.addView(end, right);
        add(form, clocks, 8);
        add(form, text("Descanso (minutos)", 13, NAVY, true), 14);
        EditText breaks = numeric(String.valueOf(draft.breakMinutes));
        add(form, breaks, 1);
        add(form, text("Moneda del turno", 13, NAVY, true), 12);
        TextView rateTitle = text("Tarifa por hora (" + draft.currencyCode + ")", 13, NAVY, true);
        final EditText[] rateInput = new EditText[1];
        TextView currencyChip = pill(draft.currencyCode + "  ·  Cambiar", LIGHT_BLUE, STRONG_BLUE);
        currencyChip.setOnClickListener(v -> currencyPicker(draft.currencyCode, code -> {
            draft.currencyCode = code;
            currencyChip.setText(code + "  ·  Cambiar");
            rateTitle.setText("Tarifa por hora (" + code + ")");
            if (existing == null && rateInput[0] != null) {
                rateInput[0].setText(String.valueOf(rateFor(code)).replace('.', ','));
            }
        }));
        add(form, currencyChip, 5);
        add(form, rateTitle, 13);
        EditText rate = numeric(String.valueOf(draft.hourlyRate).replace('.', ','));
        rateInput[0] = rate;
        add(form, rate, 1);
        add(form, text("Nota opcional", 13, NAVY, true), 14);
        EditText note = new EditText(this);
        note.setSingleLine(true);
        note.setHint("Ej.: turno de almacén");
        note.setText(draft.note);
        note.setTextSize(15);
        add(form, note, 1);
        add(form, text("Si la salida es anterior a la entrada, el turno continúa al día siguiente.",
                12, MUTED, false), 10);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Agregar turno" : "Editar turno")
                .setView(scroll)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(none -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        if (draft.startMinutes == draft.endMinutes) {
                            Toast.makeText(this, "Entrada y salida no pueden ser iguales", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int rest = Integer.parseInt(breaks.getText().toString().trim());
                        double wage = parseRate(rate.getText().toString());
                        if (rest < 0 || rest >= draft.grossMinutes() || !Double.isFinite(wage)
                                || wage < 0 || wage > 1000000000 || !CurrencyTools.valid(draft.currencyCode)) {
                            Toast.makeText(this, "Revisa la tarifa, moneda y descanso", Toast.LENGTH_LONG).show();
                            return;
                        }
                        draft.breakMinutes = rest;
                        draft.hourlyRate = wage;
                        draft.note = note.getText().toString().trim();
                        db.save(draft);
                        selectedDay = draft.date;
                        viewedMonth = YearMonth.from(draft.date);
                        // Mostrar inmediatamente el turno guardado con su moneda.
                        prefs.edit().putString("currency_code", draft.currencyCode).apply();
                        dialog.dismiss();
                        go(CALENDAR);
                        Toast.makeText(this, "Turno guardado", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Escribe valores numéricos válidos", Toast.LENGTH_LONG).show();
                    } catch (Exception ex) {
                        Toast.makeText(this, "No se pudo guardar el turno", Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }

    private void exportCSV() {
        pendingExportMonth = viewedMonth;
        pendingExportCurrency = currency();
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("text/csv");
        save.putExtra(Intent.EXTRA_TITLE, "TurnoSaldo_" + viewedMonth + "_" + currency() + ".csv");
        startActivityForResult(save, EXPORT_REQUEST);
    }

    private static String escapeCsv(String text) {
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null ||
                data.getData() == null || pendingExportMonth == null || pendingExportCurrency == null) return;
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Fecha;Entrada;Salida;Descanso (min);Horas trabajadas;Tarifa por hora;Ganancia;Moneda ISO;Nota\r\n");
        int minutes = 0;
        double total = 0;
        for (WorkEntry e : db.forMonth(pendingExportMonth)) {
            if (!pendingExportCurrency.equals(e.currencyCode)) continue;
            csv.append(e.date).append(';').append(WorkEntry.hhmm(e.startMinutes)).append(';')
                    .append(WorkEntry.hhmm(e.endMinutes)).append(';').append(e.breakMinutes).append(';')
                    .append(String.format(Locale.ROOT, "%.2f", e.workedMinutes() / 60.0)).append(';')
                    .append(String.format(Locale.ROOT, "%.2f", e.hourlyRate)).append(';')
                    .append(String.format(Locale.ROOT, "%.2f", e.earnings())).append(';')
                    .append(e.currencyCode).append(';').append(escapeCsv(e.note)).append("\r\n");
            minutes += e.workedMinutes(); total += e.earnings();
        }
        csv.append("TOTAL;;;;").append(String.format(Locale.ROOT, "%.2f", minutes / 60.0))
                .append(";;").append(String.format(Locale.ROOT, "%.2f", total)).append(';')
                .append(pendingExportCurrency).append(";\r\n");
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new Exception("Destino inaccesible");
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "CSV exportado correctamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el CSV", Toast.LENGTH_LONG).show();
        } finally { pendingExportMonth = null; pendingExportCurrency = null; }
    }

    /** Barras escaladas a los importes reales de la semana. */
    private final class IncomeChart extends View {
        private final double[] values;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        IncomeChart(double[] values) { super(MainActivity.this); this.values = Arrays.copyOf(values, 7); }
        @Override protected void onMeasure(int width, int height) {
            super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(dp(163), View.MeasureSpec.EXACTLY));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float top = dp(10), baseline = h - dp(26), height = baseline - top;
            double max = 0;
            for (double val : values) max = Math.max(max, val);
            if (max <= 0) max = 1;
            for (int i = 0; i < 4; i++) {
                float y = baseline - (height * i / 3f);
                paint.setColor(DIVIDER); paint.setStrokeWidth(dp(1));
                canvas.drawLine(dp(6), y, w - dp(6), y, paint);
            }
            float column = (w - dp(12)) / 7f;
            for (int i = 0; i < 7; i++) {
                float mid = dp(6) + (i + .5f) * column;
                float barH = (float)(height * values[i] / max);
                paint.setColor(TURQUOISE);
                canvas.drawRoundRect(new RectF(mid - column * .29f, baseline - barH,
                        mid + column * .29f, baseline), dp(4), dp(4), paint);
                paint.setColor(MUTED); paint.setTextSize(dp(10));
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(DAYS[i], mid, h - dp(8), paint);
            }
        }
    }

    /** Iconografía vectorial simple, dibujada directamente en Canvas. */
    private final class IconView extends View {
        private final String kind;
        private final int ink;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(String kind, int ink) { super(MainActivity.this); this.kind = kind; this.ink = ink; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            float size = Math.min(getWidth(), getHeight());
            float scale = size / 24f;
            c.translate((getWidth() - size)/2f, (getHeight()-size)/2f);
            c.scale(scale, scale);
            p.setColor(ink); p.setStrokeWidth(1.9f); p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND); p.setStyle(Paint.Style.STROKE);
            switch (kind) {
                case "calendar":
                    c.drawRoundRect(3, 5, 21, 21, 2, 2, p);
                    c.drawLine(3, 10, 21, 10, p);
                    c.drawLine(8, 3, 8, 7, p); c.drawLine(16, 3, 16, 7, p);
                    p.setStyle(Paint.Style.FILL);
                    for (int y=13; y<=17; y+=4) for (int x=8; x<=16; x+=4) c.drawCircle(x, y, .85f, p);
                    break;
                case "home": {
                    Path a = new Path(); a.moveTo(3, 11); a.lineTo(12, 3); a.lineTo(21, 11);
                    a.moveTo(5, 10); a.lineTo(5, 21); a.lineTo(19,21); a.lineTo(19,10);
                    a.moveTo(10,21); a.lineTo(10,14); a.lineTo(14,14); a.lineTo(14,21); c.drawPath(a,p);
                    break;
                }
                case "bars":
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(3, 12, 7, 21, 1, 1, p);
                    c.drawRoundRect(10, 7, 14, 21, 1, 1, p);
                    c.drawRoundRect(17, 3, 21, 21, 1, 1, p);
                    break;
                case "history": case "clock":
                    c.drawCircle(12,12,9,p);
                    c.drawLine(12,7,12,12,p); c.drawLine(12,12,16,14,p);
                    break;
                case "settings":
                    c.drawCircle(12,12,4,p);
                    c.drawCircle(12,12,8,p);
                    for (int i=0;i<8;i++) {
                        double a = i * Math.PI / 4;
                        c.drawLine((float)(12+9*Math.cos(a)),(float)(12+9*Math.sin(a)),
                                (float)(12+11*Math.cos(a)),(float)(12+11*Math.sin(a)),p);
                    }
                    break;
                case "plus":
                    c.drawCircle(12,12,10,p); c.drawLine(12,6,12,18,p); c.drawLine(6,12,18,12,p);
                    break;
                case "wallet":
                    c.drawRoundRect(3,6,21,20,2,2,p);
                    c.drawRoundRect(13,10,22,16,2,2,p);
                    c.drawCircle(17,13,0.8f,p);
                    break;
                case "coins":
                    c.drawOval(4,4,19,10,p); c.drawArc(4,4,19,15,0,180,false,p);
                    c.drawArc(4,9,19,20,0,180,false,p);
                    c.drawLine(4,7,4,16,p); c.drawLine(19,7,19,16,p);
                    break;
                case "back":
                    c.drawLine(16,4,8,12,p); c.drawLine(8,12,16,20,p);
                    break;
                case "next":
                    c.drawLine(8,4,16,12,p); c.drawLine(16,12,8,20,p);
                    break;
                default: c.drawCircle(12,12,7,p);
            }
            c.restore();
        }
    }
}
