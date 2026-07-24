package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Venta de la mercancia producida por la empresa (F5a). Los altos cargos (DUENO/DIRECTIVO) venden la
 * mercancia del almacen: entra al bote el neto y se QUEMA el impuesto de venta. El descuento de mercancia
 * es el gate atomico (nunca se abona al bote una venta que no pudo descontar mercancia).
 */
public final class EmpresaVentaService {

    /** Veredicto de una venta: OK con cifras, o el motivo por el que no se ejecuto. */
    public enum Estado { OK, SIN_EMPRESA, NO_AUTORIZADO, SIN_MERCANCIA }

    /**
     * Cifras de la venta para pintar el embed. En los estados de error todas van a cero (fabrica {@link #de}).
     *
     * @param estado   veredicto
     * @param unidades unidades efectivamente vendidas
     * @param bruto    dinero bruto (unidades * precio)
     * @param impuesto parte quemada del bruto (no va a nadie)
     * @param neto     dinero ingresado al bote (bruto − impuesto)
     * @param restante mercancia que queda en el almacen tras la venta (estimacion de display: snapshot del
     *                 disponible leido, no autoritativa; un currar concurrente pudo sumar mercancia)
     */
    public record Resultado(Estado estado, long unidades, long bruto, long impuesto, long neto, long restante) {
        static Resultado de(Estado estado) { return new Resultado(estado, 0, 0, 0, 0, 0); }
    }

    private final EmpresaRepositorio repo;

    public EmpresaVentaService(EmpresaRepositorio repo) { this.repo = repo; }

    /**
     * Vende mercancia del almacen de la empresa del actor. {@code cantidad} vacio = vender todo. Valida:
     * pertenece a una empresa, es alto cargo, hay mercancia. Ejecuta: gastarMercancia (gate) ->
     * incrementarBote(neto); el impuesto se quema.
     *
     * <p>El descuento de mercancia va SIEMPRE antes del abono: gastarMercancia es condicional en BD
     * ({@code mercancia >= ?}), asi que si otra venta simultanea vacio el almacen devuelve false y no se
     * abona nada. Se vende como mucho lo disponible ({@code Math.min}) para no cobrar mercancia inexistente.
     */
    public Resultado vender(long actorId, OptionalLong cantidad) {
        Optional<Empresa> empOpt = repo.deMiembro(actorId);
        if (empOpt.isEmpty()) return Resultado.de(Estado.SIN_EMPRESA);
        Empresa emp = empOpt.get();
        if (!esAltoCargo(emp.id(), actorId)) return Resultado.de(Estado.NO_AUTORIZADO);
        long disponible = emp.mercancia();
        // Sin cantidad = vender todo; con cantidad, nunca mas de lo que hay en almacen.
        long aVender = cantidad.isPresent() ? Math.min(cantidad.getAsLong(), disponible) : disponible;
        if (aVender <= 0) return Resultado.de(Estado.SIN_MERCANCIA);
        // Gate atomico: si el descuento condicional no prospera (almacen ya vaciado), no se abona nada.
        if (!repo.gastarMercancia(emp.id(), aVender)) return Resultado.de(Estado.SIN_MERCANCIA);
        // bruto acotado: mercancia <= nivel*100 (Produccion.capacidad), no desborda long
        long bruto = aVender * Produccion.PRECIO_UNIDAD;
        long impuesto = Produccion.impuesto(bruto);
        long neto = bruto - impuesto;
        repo.incrementarBote(emp.id(), neto); // el impuesto NO se ingresa a nadie: se quema
        // restante es una estimacion de display (snapshot del disponible leido): un currar concurrente
        // pudo sumar mercancia entre la lectura y ahora, asi que no es autoritativa (la BD lo es).
        return new Resultado(Estado.OK, aVender, bruto, impuesto, neto, disponible - aVender);
    }

    /**
     * Alto cargo = dueño o directivo: los unicos que pueden mover el dinero de la empresa. Reusa
     * {@code altosCargos} (filtra {@code rango IN ('DUENO','DIRECTIVO')} en SQL) en vez de traerse toda
     * la plantilla y filtrar en memoria, misma fuente que {@code EmpresaGestionService}.
     */
    private boolean esAltoCargo(long empresaId, long actorId) {
        return repo.altosCargos(empresaId).stream().anyMatch(m -> m.discordId() == actorId);
    }
}
