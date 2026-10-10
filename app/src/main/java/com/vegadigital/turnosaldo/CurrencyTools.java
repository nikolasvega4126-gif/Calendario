package com.vegadigital.turnosaldo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Divisas ISO 4217 disponibles en la versión de Android instalada. Sin tipos de cambio. */
public final class CurrencyTools {
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private CurrencyTools() {}

    public static String localCurrency() {
        try {
            Currency c = Currency.getInstance(Locale.getDefault());
            return c == null ? "USD" : c.getCurrencyCode();
        } catch (Exception ignored) { return "USD"; }
    }

    public static boolean valid(String code) {
        try { return !"XXX".equals(code) && Currency.getInstance(code) != null; }
        catch (Exception ignored) { return false; }
    }

    public static String name(String code) {
        try { return Currency.getInstance(code).getDisplayName(ES); }
        catch (Exception ignored) { return code; }
    }

    public static List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (Currency c : Currency.getAvailableCurrencies()) {
            String code = c.getCurrencyCode();
            if (!"XXX".equals(code)) labels.add(code + "  ·  " + c.getDisplayName(ES));
        }
        Collections.sort(labels, Comparator.comparing(s -> s.substring(0, 3)));
        return labels;
    }

    public static String unit(String code) {
        switch (code) {
            case "PLN": return "zł";
            case "EUR": return "€";
            case "GBP": return "£";
            case "JPY": return "¥";
            case "CNY": return "¥ CNY";
            case "INR": return "₹";
            case "KRW": return "₩";
            case "RUB": return "₽";
            case "TRY": return "₺";
            case "UAH": return "₴";
            case "BRL": return "R$";
            // La mayoría de divisas con signo $ se identifican por su código.
            default: return code;
        }
    }

    public static String money(double amount, String code) {
        int precision = 2;
        try {
            int digits = Currency.getInstance(code).getDefaultFractionDigits();
            if (digits >= 0 && digits <= 3) precision = digits;
        } catch (Exception ignored) { /* fallback */ }
        StringBuilder pattern = new StringBuilder("#,##0");
        if (precision > 0) {
            pattern.append('.');
            for (int i = 0; i < precision; i++) pattern.append('0');
        }
        DecimalFormat f = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return f.format(amount) + " " + unit(code);
    }
}
