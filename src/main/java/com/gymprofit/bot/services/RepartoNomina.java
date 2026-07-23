package com.gymprofit.bot.services;

import com.gymprofit.bot.db.MiembroEmpresa;

import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo puro de la nómina diaria de una empresa: reparte una fracción del bote entre sus miembros
 * proporcionalmente al peso de su rango. Sin estado ni BD, para poder testear la aritmética aislada;
 * la orquestación (leer el bote, gastarlo, ingresar a cada uno) vive en {@code jobs/NominaEmpresasJob}.
 *
 * <p>Del bote se reparte {@link #FRACCION_NOMINA} (el {@code pool}); el resto se queda acumulado. El
 * peso de cada miembro es {@code rango.ordinal()+1} (BECARIO=1 … DUENO=5), así el enum ordena el
 * reparto sin tabla aparte. Todo se redondea <b>a la baja</b>: la calderilla que no cuadra se queda en
 * el bote para la próxima nómina, nunca se inventan coins.
 */
public final class RepartoNomina {

    /** Fracción del bote que se reparte cada día como nómina. */
    public static final double FRACCION_NOMINA = 0.20;

    private RepartoNomina() {
    }

    /** Una parte de la nómina: cuánto le toca a un miembro. */
    public record ParteNomina(long discordId, long parte) {
    }

    /**
     * Reparte {@code floor(bote * FRACCION_NOMINA)} entre los miembros según el peso de su rango
     * ({@code ordinal()+1}), con redondeo a la baja por miembro. Función pura.
     *
     * <p>Devuelve una parte por miembro <b>incluyendo las de valor 0</b> (bote insuficiente para ese
     * peso): el job las ignora al ingresar. Con la lista de miembros vacía devuelve lista vacía.
     *
     * @param bote     fondo común de la empresa
     * @param miembros miembros entre los que repartir
     * @return una {@link ParteNomina} por miembro, en el mismo orden de entrada
     */
    public static List<ParteNomina> calcular(long bote, List<MiembroEmpresa> miembros) {
        long pool = (long) Math.floor(bote * FRACCION_NOMINA);
        int sumaPesos = miembros.stream().mapToInt(m -> m.rango().ordinal() + 1).sum();

        List<ParteNomina> partes = new ArrayList<>(miembros.size());
        for (MiembroEmpresa m : miembros) {
            long parte = sumaPesos == 0
                    ? 0
                    : (long) Math.floor((double) pool * (m.rango().ordinal() + 1) / sumaPesos);
            partes.add(new ParteNomina(m.discordId(), parte));
        }
        return partes;
    }
}
