package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;

/**
 * Cobro del impuesto semanal de una empresa (F5b): decide y aplica PAGA / MOROSA / QUIEBRA. La decision
 * ({@link #evaluar}) es pura y testeable; {@link #aplicar} ejecuta el efecto sobre el repo. El dinero se
 * quema con {@link EmpresaRepositorio#gastarDelBote} (gate atomico): si el bote bajo entre evaluar y
 * cobrar, el gasto falla y se cuenta como impago (nunca se quema de mas ni se paga a medias).
 */
public final class ImpuestoEmpresasService {

    public enum Tipo { PAGA, MOROSA, QUIEBRA }

    /** Resolucion del cobro de una empresa: tipo + cifras para el aviso. */
    public record Resolucion(Tipo tipo, long cuota, int impagos, long falta) {}

    private final EmpresaRepositorio repo;

    public ImpuestoEmpresasService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /** Decide (sin tocar nada) que hacer con una empresa este cobro. */
    public Resolucion evaluar(Empresa e) {
        long cuota = Impuesto.cuota(e.nivel());
        if (e.bote() >= cuota) {
            return new Resolucion(Tipo.PAGA, cuota, 0, 0);
        }
        int nuevos = e.impagos() + 1;
        Tipo tipo = nuevos >= Impuesto.MOROSIDAD_MAX ? Tipo.QUIEBRA : Tipo.MOROSA;
        return new Resolucion(tipo, cuota, nuevos, cuota - e.bote());
    }

    /**
     * Aplica el cobro y devuelve la resolucion REAL (para el aviso). PAGA intenta el gate atomico
     * gastarDelBote: si falla (carrera), degrada a impago (morosa o quiebra segun el contador). QUIEBRA
     * disuelve. MOROSA solo actualiza el contador.
     */
    public Resolucion aplicar(Empresa e) {
        Resolucion r = evaluar(e);
        switch (r.tipo()) {
            case PAGA -> {
                if (repo.gastarDelBote(e.id(), r.cuota())) {
                    repo.fijarImpagos(e.id(), 0);
                    return r;
                }
                return recaerEnImpago(e, r.cuota()); // carrera: el bote ya no cubre
            }
            case MOROSA -> repo.fijarImpagos(e.id(), r.impagos());
            case QUIEBRA -> repo.disolver(e.id());
        }
        return r;
    }

    /** Un pago que falla en el gate se trata como impago: cuenta uno mas y quiebra si toca. */
    private Resolucion recaerEnImpago(Empresa e, long cuota) {
        int nuevos = e.impagos() + 1;
        // falta real segun el snapshot que teniamos (igual que evaluar), no la cuota entera: en la
        // carrera el bote no cubre pero puede no estar a 0, y el aviso no debe exagerar cuanto falta.
        long falta = cuota - e.bote();
        if (nuevos >= Impuesto.MOROSIDAD_MAX) {
            repo.disolver(e.id());
            return new Resolucion(Tipo.QUIEBRA, cuota, nuevos, falta);
        }
        repo.fijarImpagos(e.id(), nuevos);
        return new Resolucion(Tipo.MOROSA, cuota, nuevos, falta);
    }
}
