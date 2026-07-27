package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaAccionRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Participaciones de empresa (F5): comprar/vender contra el pool fijo de la empresa. Todo es
 * redistribución (comprar mueve coins jugador→bote; vender, bote→jugador): cero creación. El precio de la
 * participación flota con el prestigio ({@link Accion#precioParticipacion}). Los movimientos siguen el
 * patrón atómico del resto (validar → gate del dinero → registrar): nunca dejan estado a medias.
 */
public final class AccionEmpresasService {

    public enum EstadoCompra { OK, NO_EXISTE, CANTIDAD_INVALIDA, SIN_PARTICIPACIONES_LIBRES, SIN_SALDO }
    public enum EstadoVenta { OK, NO_EXISTE, CANTIDAD_INVALIDA, SIN_PARTICIPACIONES, EMPRESA_SIN_FONDOS }

    public record ResultadoCompra(EstadoCompra estado, int cantidad, long precio, long coste) {
        static ResultadoCompra de(EstadoCompra e) { return new ResultadoCompra(e, 0, 0, 0); }
    }
    public record ResultadoVenta(EstadoVenta estado, int cantidad, long precio, long valor) {
        static ResultadoVenta de(EstadoVenta e) { return new ResultadoVenta(e, 0, 0, 0); }
    }

    /** Una posición para pintar la cartera: empresa, participaciones, precio actual y valor. */
    public record PosicionVista(String empresa, int cantidad, long precio, long valor) {}

    private final EmpresaRepositorio repo;
    private final EmpresaAccionRepositorio accRepo;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public AccionEmpresasService(EmpresaRepositorio repo, EmpresaAccionRepositorio accRepo,
                                 EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this.repo = repo;
        this.accRepo = accRepo;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Precio actual de una participación de la empresa (prestigio / pool, suelo 1). */
    public long precioActual(Empresa e) {
        long prestigio = Prestigio.calcular(e.nivel(), repo.miembros(e.id()).size(), e.bote());
        return Accion.precioParticipacion(prestigio);
    }

    /** Compra {@code cantidad} participaciones: cobra al jugador y el capital entra al bote. */
    public ResultadoCompra comprar(long actorId, long empresaId, int cantidad) {
        if (cantidad <= 0) return ResultadoCompra.de(EstadoCompra.CANTIDAD_INVALIDA);
        Optional<Empresa> empOpt = repo.porId(empresaId);
        if (empOpt.isEmpty()) return ResultadoCompra.de(EstadoCompra.NO_EXISTE);
        Empresa emp = empOpt.get();
        int libres = Accion.POOL - accRepo.vendidasDe(empresaId);
        if (cantidad > libres) return ResultadoCompra.de(EstadoCompra.SIN_PARTICIPACIONES_LIBRES);
        long precio = precioActual(emp);
        long coste = precio * cantidad;
        usuarios.obtenerOCrear(actorId);
        if (!economia.gastar(actorId, coste, "acciones_comprar:" + empresaId)) {
            return ResultadoCompra.de(EstadoCompra.SIN_SALDO);
        }
        repo.incrementarBote(empresaId, coste);
        int nuevas = accRepo.participacionesDe(empresaId, actorId) + cantidad;
        accRepo.fijar(empresaId, actorId, nuevas);
        return new ResultadoCompra(EstadoCompra.OK, cantidad, precio, coste);
    }

    /** Vende {@code cantidad} participaciones de vuelta al pool: la empresa paga del bote a precio actual. */
    public ResultadoVenta vender(long actorId, long empresaId, int cantidad) {
        if (cantidad <= 0) return ResultadoVenta.de(EstadoVenta.CANTIDAD_INVALIDA);
        Optional<Empresa> empOpt = repo.porId(empresaId);
        if (empOpt.isEmpty()) return ResultadoVenta.de(EstadoVenta.NO_EXISTE);
        Empresa emp = empOpt.get();
        int tienes = accRepo.participacionesDe(empresaId, actorId);
        if (tienes < cantidad) return ResultadoVenta.de(EstadoVenta.SIN_PARTICIPACIONES);
        long precio = precioActual(emp);
        long valor = precio * cantidad;
        if (!repo.gastarDelBote(empresaId, valor)) {
            return ResultadoVenta.de(EstadoVenta.EMPRESA_SIN_FONDOS);
        }
        usuarios.obtenerOCrear(actorId);
        economia.ingresar(actorId, valor, "acciones_vender:" + empresaId);
        accRepo.fijar(empresaId, actorId, tienes - cantidad);
        return new ResultadoVenta(EstadoVenta.OK, cantidad, precio, valor);
    }

    /** Cartera de participaciones del jugador, valorada a precio actual por empresa. */
    public List<PosicionVista> cartera(long actorId) {
        List<PosicionVista> vistas = new ArrayList<>();
        for (EmpresaAccionRepositorio.PosicionAccion pos : accRepo.carteraDe(actorId)) {
            Optional<Empresa> emp = repo.porId(pos.empresaId());
            if (emp.isEmpty()) continue;
            long precio = precioActual(emp.get());
            vistas.add(new PosicionVista(emp.get().nombre(), pos.cantidad(), precio, precio * pos.cantidad()));
        }
        return vistas;
    }
}
