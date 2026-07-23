package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.admin.RegistroCambios.Categoria;
import com.gymprofit.bot.commands.admin.RegistroCambios.Entrada;
import com.gymprofit.bot.commands.admin.RegistroCambios.Tipo;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renderiza un {@link RegistroCambios} a las líneas de texto del informe de {@code /setup}, ya
 * localizadas. Puro y testeable: no envía nada; el troceado y el envío los hace {@code SetupComando}.
 * Agrupa por tipo de cambio (nuevos → actualizados → eliminados) y, dentro, por categoría.
 */
public final class InformeSetup {

    /** Datos de cabecera del informe. */
    public record Contexto(String servidor, String porMencion, boolean desdeCero, long epochSegundos) {}

    private InformeSetup() {}

    public static List<String> lineas(RegistroCambios registro, Contexto ctx, Locale locale) {
        List<String> l = new ArrayList<>();
        l.add(Messages.get(locale, "setup.registro.cabecera",
                ctx.servidor(), ctx.porMencion(),
                Messages.get(locale, ctx.desdeCero()
                        ? "setup.registro.modo.desde_cero" : "setup.registro.modo.normal"),
                EmbedFactory.fechaLarga(ctx.epochSegundos())));
        l.add(Messages.get(locale, "setup.registro.contadores",
                registro.cuenta(Tipo.CREADO), registro.cuenta(Tipo.ACTUALIZADO),
                registro.cuenta(Tipo.ELIMINADO)));

        if (!registro.huboCambios()) {
            l.add(Messages.get(locale, "setup.registro.sincambios"));
            return l;
        }

        for (Tipo tipo : Tipo.values()) {
            List<Entrada> deTipo = registro.entradas().stream().filter(e -> e.tipo() == tipo).toList();
            if (deTipo.isEmpty()) {
                continue;
            }
            l.add("");
            l.add("**" + Messages.get(locale, claveTitulo(tipo)) + "**");
            // Agrupa por categoría preservando el orden de aparición.
            Map<Categoria, List<String>> porCategoria = new LinkedHashMap<>();
            for (Entrada e : deTipo) {
                porCategoria.computeIfAbsent(e.categoria(), k -> new ArrayList<>()).add(e.nombre());
            }
            for (Map.Entry<Categoria, List<String>> ent : porCategoria.entrySet()) {
                l.add("__" + Messages.get(locale, claveCategoria(ent.getKey())) + "__: "
                        + String.join(", ", ent.getValue()));
            }
        }
        return l;
    }

    private static String claveTitulo(Tipo tipo) {
        return switch (tipo) {
            case CREADO -> "setup.registro.nuevos";
            case ACTUALIZADO -> "setup.registro.actualizados";
            case ELIMINADO -> "setup.registro.eliminados";
        };
    }

    private static String claveCategoria(Categoria categoria) {
        return "setup.registro.categoria." + categoria.name().toLowerCase(Locale.ROOT);
    }
}
