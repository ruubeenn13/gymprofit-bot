package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio.EmpresaRanking;

import java.util.List;

/**
 * Cuota de mercado de una empresa (F5): la parte del prestigio de su rama. Deriva (no persiste) el factor
 * que escala sus ventas ({@link #factorVentaDe}) y el porcentaje de cuota para el display
 * ({@link #cuotaDe}). Sin competencia (una sola empresa en la rama) el factor es neutro (1.0).
 */
public final class CuotaEmpresasService {

    private final EmpresaRepositorio repo;

    public CuotaEmpresasService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /** Factor multiplicador de venta según la cuota de la empresa en su rama (±25 %, neutro 1.0). */
    public double factorVentaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        if (rama.size() <= 1) {
            return 1.0;
        }
        Reparto r = repartoDe(rama, e.nombre());
        return Cuota.factorVenta(Cuota.relativa(r.propio(), r.total(), rama.size()));
    }

    /** Cuota de la empresa en su rama en tanto por uno (0..1) para el display; 0 si la rama no suma. */
    public double cuotaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        Reparto r = repartoDe(rama, e.nombre());
        return r.total() <= 0 ? 0 : (double) r.propio() / r.total();
    }

    /** Cuota (tanto por uno) y factor de venta juntos, para el display de {@code /empresa info}. */
    public record CuotaVista(double cuota, double factorVenta) {}

    /**
     * Cuota y factor de venta de una empresa en <b>una sola lectura</b> de la rama, para no repetir el
     * {@code rankingDeRama} en {@code /empresa info} (donde se pintan ambos valores). Compone la semántica
     * de {@link #cuotaDe} (proporción propio/total, 0 si la rama no suma) y {@link #factorVentaDe} (neutro
     * 1.0 sin competencia; si no, {@link Cuota#factorVenta} sobre la cuota relativa).
     */
    public CuotaVista vistaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        Reparto r = repartoDe(rama, e.nombre());
        double cuota = r.total() <= 0 ? 0 : (double) r.propio() / r.total();
        double factor = rama.size() <= 1 ? 1.0
                : Cuota.factorVenta(Cuota.relativa(r.propio(), r.total(), rama.size()));
        return new CuotaVista(cuota, factor);
    }

    /** Prestigio propio dentro del total de la rama, para {@link #factorVentaDe} y {@link #cuotaDe}. */
    private record Reparto(long propio, long total) {}

    private Reparto repartoDe(List<EmpresaRanking> rama, String nombre) {
        long total = 0;
        long propio = 0;
        for (EmpresaRanking r : rama) {
            long p = Prestigio.calcular(r.nivel(), r.miembros(), r.bote());
            total += p;
            // Si nombre no aparece en su propia rama (solo posible en una carrera de rename/disolución),
            // propio se queda a 0 -> penaliza en vez de romper la venta con una excepción.
            if (r.nombre().equals(nombre)) {
                propio = p;
            }
        }
        return new Reparto(propio, total);
    }
}
