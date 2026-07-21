package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.Pasivos;
import com.gymprofit.bot.services.Pasivos.Tipo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Constructores de vista de los efectos pasivos: estáticos, puros y sin JDA, para poder testearlos
 * sin levantar nada. Viven en {@code commands/economia} porque los usan dos comandos del mismo
 * paquete: {@code /pasivos ver} y (a partir de la Task 11) la línea nueva de {@code /perfil ver}.
 *
 * <p>Regla de presentación: se enseña el <b>resultado</b>, no la aritmética (+18 % sueldo, nunca
 * «+11 % +7 %»), y si un tipo está saturado se marca con «(tope)» para que nadie equipe una cuarta
 * pieza del mismo tipo sin enterarse de que no le suma nada.
 */
public final class PasivosTexto {

    /** Emojis de ranura, en orden 1..4. */
    private static final String[] NUMEROS = {"1️⃣", "2️⃣", "3️⃣", "4️⃣"};

    private PasivosTexto() {
    }

    /** Nombre localizado de un tipo de bono ({@code sueldo}, {@code XP}…). */
    public static String nombreTipo(Locale locale, Tipo tipo) {
        return Messages.get(locale, "pasivos.tipo." + switch (tipo) {
            case SUELDO -> "sueldo";
            case COOLDOWN_WORK -> "cooldown";
            case XP -> "xp";
            case ENERGIA_REGEN -> "energia";
            case MINERIA_CANTIDAD -> "mineria";
            case MINERIA_DURABILIDAD -> "durabilidad";
            case COMBATE_ATAQUE -> "ataque";
            case COMBATE_DEFENSA -> "defensa";
            case CRITICO -> "critico";
        });
    }

    /**
     * Línea de bonos activos, ya sumados y ya topados, separados por «·». Cadena <b>vacía</b> si no
     * hay ninguno: así el perfil de un jugador nuevo no se ensucia con una línea a ceros.
     *
     * @param bonos mapa tipo → magnitud tal cual lo devuelve {@code PasivoService.bonosDe}; se
     *              tolera que falten tipos (se leen como 0,0)
     */
    public static String bonos(Locale locale, Map<Tipo, Double> bonos) {
        StringJoiner sj = new StringJoiner(" · ");
        for (Tipo t : Tipo.values()) {
            double v = bonos.getOrDefault(t, 0.0);
            if (v <= 0) {
                continue;
            }
            // El cooldown es lo único que resta: se pinta con el menos tipográfico, no con guion.
            String signo = t == Tipo.COOLDOWN_WORK ? "−" : "+";
            String texto = Pasivos.esPorcentual(t)
                    ? Messages.get(locale, "pasivos.bono.pct", signo,
                            Math.round(v * 100), nombreTipo(locale, t))
                    : Messages.get(locale, "pasivos.bono.plano", signo,
                            Math.round(v), nombreTipo(locale, t));
            // Saturado: hay que decirlo, o el jugador seguirá comprando piezas del mismo tipo.
            if (v >= Pasivos.TOPES.get(t)) {
                texto = texto + " " + Messages.get(locale, "pasivos.ver.tope");
            }
            sj.add(texto);
        }
        return sj.toString();
    }

    /** Las cuatro ranuras en orden, cada una con su estado (ocupada, vacía, bloqueada o ausente). */
    public static String ranuras(Locale locale, List<EstadoRanura> ranuras) {
        StringBuilder sb = new StringBuilder();
        for (EstadoRanura r : ranuras) {
            String num = NUMEROS[r.ranura() - 1];
            if (r.bloqueada()) {
                sb.append(Messages.get(locale, "pasivos.ver.ranura.bloqueada", num,
                        PasivoService.nivelDeRanura(r.ranura())));
            } else if (r.itemId() == null) {
                sb.append(Messages.get(locale, "pasivos.ver.ranura.vacia", num));
            } else {
                String emoji = emoji(r.itemId());
                String nombre = nombre(locale, r.itemId());
                sb.append(Messages.get(locale,
                        r.falta() ? "pasivos.ver.ranura.sinitem" : "pasivos.ver.ranura.ocupada",
                        num, emoji, nombre));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Pie con el siguiente desbloqueo, o el mensaje de «ya las tienes todas». */
    public static String pie(Locale locale, int nivel) {
        int abiertas = PasivoService.ranurasDe(nivel);
        if (abiertas >= PasivoService.RANURAS_MAX) {
            return Messages.get(locale, "pasivos.ver.pie.completo");
        }
        int siguiente = abiertas + 1;
        int nivelNecesario = PasivoService.nivelDeRanura(siguiente);
        return Messages.get(locale, "pasivos.ver.pie", siguiente, nivelNecesario,
                nivelNecesario - nivel);
    }

    /** Descripción localizada del pasivo de un ítem (la que explica el sistema desde dentro). */
    public static String descripcion(Locale locale, String itemId) {
        return Messages.get(locale, "pasivo." + itemId + ".desc");
    }

    /** Emoji del ítem; interrogante si el id no está en el catálogo (dato viejo en BD). */
    static String emoji(String itemId) {
        return Items.porId(itemId).map(Items::emoji).orElse("❔");
    }

    /** Nombre localizado del ítem; si el id no existe en el catálogo, el id crudo. */
    static String nombre(Locale locale, String itemId) {
        return Items.porId(itemId).map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
    }
}
