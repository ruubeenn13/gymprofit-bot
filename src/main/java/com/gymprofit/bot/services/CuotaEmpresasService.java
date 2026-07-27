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
        long total = 0;
        long propio = 0;
        for (EmpresaRanking r : rama) {
            long p = Prestigio.calcular(r.nivel(), r.miembros(), r.bote());
            total += p;
            if (r.nombre().equals(e.nombre())) {
                propio = p;
            }
        }
        return Cuota.factorVenta(Cuota.relativa(propio, total, rama.size()));
    }

    /** Cuota de la empresa en su rama en tanto por uno (0..1) para el display; 0 si la rama no suma. */
    public double cuotaDe(Empresa e) {
        List<EmpresaRanking> rama = repo.rankingDeRama(e.rama());
        long total = 0;
        long propio = 0;
        for (EmpresaRanking r : rama) {
            long p = Prestigio.calcular(r.nivel(), r.miembros(), r.bote());
            total += p;
            if (r.nombre().equals(e.nombre())) {
                propio = p;
            }
        }
        return total <= 0 ? 0 : (double) propio / total;
    }
}
