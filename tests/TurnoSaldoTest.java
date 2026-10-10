import com.vegadigital.turnosaldo.CurrencyTools;
import com.vegadigital.turnosaldo.WorkEntry;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/** Pruebas básicas ejecutables sin instalar el Android SDK. */
public class TurnoSaldoTest {
    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LocalDate day = LocalDate.of(2026,10,10);
        WorkEntry normal = new WorkEntry(0, day, 8*60, 16*60, 30, 30, "PLN", "");
        ok(normal.workedMinutes() == 450 && normal.earnings() == 225, "Turno con pausa");
        WorkEntry night = new WorkEntry(0, day, 22*60, 6*60, 60, 35, "EUR", "noche");
        ok(night.grossMinutes() == 480 && night.workedMinutes() == 420
                && night.earnings() == 245, "Turno nocturno");
        WorkEntry sameNumbersOtherCurrency = new WorkEntry(0, day, 8*60, 16*60, 30,
                30, "COP", "");
        ok(sameNumbersOtherCurrency.currencyCode.equals("COP") &&
                normal.currencyCode.equals("PLN"), "Divisa persistida en cada turno");
        for (String code : new String[]{"COP", "MXN", "EUR", "USD", "PLN", "GBP", "JPY", "BRL"}) {
            ok(CurrencyTools.valid(code), "ISO aceptado: " + code);
            ok(CurrencyTools.money(320, code).length() > 3, "Formato de " + code);
        }
        ok(!CurrencyTools.valid("BTC") && !CurrencyTools.valid("NONSENSE"), "Solo divisas ISO reconocidas");
        ok(CurrencyTools.money(1000.25, "JPY").equals(CurrencyTools.money(1000, "JPY")), "JPY sin decimales");
        List<String> labels = CurrencyTools.labels();
        ok(labels.size() > 100, "Catálogo de monedas global");
        System.out.println("Pruebas superadas: turnos, monedas, formato y catálogo ("
                + labels.size() + " divisas del entorno JVM)");
    }
}
