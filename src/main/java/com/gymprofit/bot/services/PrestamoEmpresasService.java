package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Prestamos empresariales (F5d): conceder (el principal entra al bote, se fija deuda+cuota) y amortizar
 * (pagar del bote contra la deuda). Uno a la vez. Autorizan los altos cargos (DUENO/DIRECTIVO). El gate
 * de dinero es {@link EmpresaRepositorio#gastarDelBote}; el principal entra con {@code incrementarBote}.
 *
 * <p>Los numeros (limite por nivel, interes y plazo) viven en {@link Prestamo}; aqui solo se orquesta el
 * flujo y se persiste el estado calculado via {@link EmpresaRepositorio#fijarPrestamo}.
 */
public final class PrestamoEmpresasService {

    /** Resultado de conceder un prestamo: OK o el motivo del rechazo. */
    public enum EstadoConceder { OK, SIN_EMPRESA, NO_AUTORIZADO, CANTIDAD_INVALIDA, YA_TIENE_PRESTAMO, LIMITE }

    /** Resultado de amortizar un prestamo: OK o el motivo del rechazo. */
    public enum EstadoPago { OK, SIN_EMPRESA, NO_AUTORIZADO, SIN_DEUDA, SIN_FONDOS }

    /**
     * Resultado de conceder: el principal recibido, la deuda total con interes, la cuota semanal y el
     * limite del nivel (util en LIMITE para decir cuanto se podia pedir como mucho).
     */
    public record ResultadoConceder(EstadoConceder estado, long principal, long deuda, long cuota, long limite) {
        static ResultadoConceder de(EstadoConceder e) { return new ResultadoConceder(e, 0, 0, 0, 0); }
    }

    /** Resultado de amortizar: lo pagado en esta operacion, la deuda que queda y la cuota resultante. */
    public record ResultadoPago(EstadoPago estado, long pagado, long deudaRestante, long cuota) {
        static ResultadoPago de(EstadoPago e) { return new ResultadoPago(e, 0, 0, 0); }
    }

    private final EmpresaRepositorio repo;

    public PrestamoEmpresasService(EmpresaRepositorio repo) {
        this.repo = repo;
    }

    /**
     * Concede un prestamo a la empresa del actor: valida (miembro, alto cargo, cantidad, sin prestamo
     * activo, dentro del limite) y, si pasa, ingresa el principal al bote e inmediatamente fija la deuda
     * con interes y su cuota semanal. Un prestamo a la vez: si ya hay deuda, se rechaza.
     *
     * @param actorId quien pide el prestamo (debe ser DUENO/DIRECTIVO de la empresa)
     * @param cantidad principal solicitado (coins que entran al bote)
     * @return el resultado con las cifras; en LIMITE incluye el maximo del nivel
     */
    public ResultadoConceder conceder(long actorId, long cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) return ResultadoConceder.de(EstadoConceder.SIN_EMPRESA);
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) return ResultadoConceder.de(EstadoConceder.NO_AUTORIZADO);
        if (cantidad <= 0) return ResultadoConceder.de(EstadoConceder.CANTIDAD_INVALIDA);
        if (emp.deuda() > 0) return ResultadoConceder.de(EstadoConceder.YA_TIENE_PRESTAMO);
        long limite = Prestamo.limite(emp.nivel());
        if (cantidad > limite) return new ResultadoConceder(EstadoConceder.LIMITE, 0, 0, 0, limite);
        long deuda = Prestamo.deudaConInteres(cantidad);
        long cuota = Prestamo.cuota(deuda);
        // El principal entra al bote y la deuda/cuota se fija JUSTO despues: la empresa recibe el credito y
        // queda anotada la obligacion de devolverlo (con interes) en cuotas semanales.
        repo.incrementarBote(emp.id(), cantidad);
        repo.fijarPrestamo(emp.id(), deuda, cuota);
        return new ResultadoConceder(EstadoConceder.OK, cantidad, deuda, cuota, limite);
    }

    /**
     * Amortiza el prestamo de la empresa del actor pagando del bote contra la deuda. Con cantidad, paga
     * {@code min(cantidad, deuda)}; sin cantidad, paga {@code min(deuda, bote)} (lo que se pueda). El
     * descuento del bote es el <b>gate</b>: si {@link EmpresaRepositorio#gastarDelBote} devuelve false (sin
     * saldo o carrera perdida) NO se baja la deuda. Al saldar (deuda a 0) la cuota queda tambien a 0.
     *
     * @param actorId quien amortiza (debe ser DUENO/DIRECTIVO de la empresa)
     * @param cantidad cuanto pagar; vacio = lo que permita el bote
     * @return el resultado con lo pagado, la deuda restante y la cuota resultante
     */
    public ResultadoPago pagar(long actorId, OptionalLong cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) return ResultadoPago.de(EstadoPago.SIN_EMPRESA);
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) return ResultadoPago.de(EstadoPago.NO_AUTORIZADO);
        if (emp.deuda() <= 0) return ResultadoPago.de(EstadoPago.SIN_DEUDA);
        // Con cantidad: nunca pagar mas que la deuda. Sin cantidad: lo que el bote permita, sin pasarse.
        long tope = cantidad.isPresent() ? Math.min(cantidad.getAsLong(), emp.deuda())
                                         : Math.min(emp.deuda(), emp.bote());
        if (tope <= 0) return ResultadoPago.de(EstadoPago.SIN_FONDOS);
        if (!repo.gastarDelBote(emp.id(), tope)) return ResultadoPago.de(EstadoPago.SIN_FONDOS);
        long deudaNueva = emp.deuda() - tope;
        // Al saldar la deuda no queda cuota; si aun hay deuda, la cuota no puede superar lo que resta.
        long cuotaNueva = deudaNueva == 0 ? 0 : Math.min(emp.cuotaPrestamo(), deudaNueva);
        repo.fijarPrestamo(emp.id(), deudaNueva, cuotaNueva);
        return new ResultadoPago(EstadoPago.OK, tope, deudaNueva, cuotaNueva);
    }

    /** Un actor es alto cargo si aparece entre los DUENO/DIRECTIVO que devuelve el repo (filtro en SQL). */
    private boolean esAltoCargo(long empresaId, long actorId) {
        return repo.altosCargos(empresaId).stream().anyMatch(m -> m.discordId() == actorId);
    }
}
