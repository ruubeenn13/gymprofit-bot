package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPendiente;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC de empresas ({@code empresas}, {@code empresa_miembros}, {@code empresa_pendientes}).
 * Sigue el patrón de la capa {@code db/}: recibe el {@link DataSource} del {@link Database} y usa una
 * conexión del pool por operación, con sentencias parametrizadas. La pertenencia es exclusiva (un
 * jugador en una sola empresa) por el UNIQUE {@code uq_miembro_unico}; las violaciones de UNIQUE se
 * propagan como {@link DatabaseException} para que el service las traduzca a error de usuario.
 */
public final class EmpresaRepositorio {

    private final DataSource dataSource;

    public EmpresaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Funda una empresa: inserta la fila de {@code empresas} y da de alta al dueño como {@link
     * RangoEmpresa#DUENO} en {@code empresa_miembros}, todo en UNA transacción para no dejar una
     * empresa sin dueño si el segundo insert falla. Devuelve el id generado.
     *
     * <p>El {@code rollback} en el {@code catch} garantiza la atomicidad; la excepción se propaga
     * (envuelta) para que el service distinga la violación de {@code uq_empresa_nombre_rama} (nombre
     * repetido en la rama) o {@code uq_miembro_unico} (el dueño ya está en otra empresa) y la
     * traduzca a un mensaje de usuario. No se restaura {@code autoCommit} a mano: al cerrar la
     * conexión (try-with-resources) el pool la devuelve reseteada, igual que el resto de repos.
     */
    public long fundar(String rama, long duenoId, String nombre) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO empresas (rama, dueno_discord_id, nombre) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, rama);
                    ps.setLong(2, duenoId);
                    ps.setString(3, nombre);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO empresa_miembros (empresa_id, discord_id, rango) VALUES (?, ?, ?)")) {
                    ps.setLong(1, id);
                    ps.setLong(2, duenoId);
                    ps.setString(3, RangoEmpresa.DUENO.name());
                    ps.executeUpdate();
                }
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error fundando la empresa " + nombre + " (" + rama + ")", e);
        }
    }

    /** Empresa por id. */
    public Optional<Empresa> porId(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_EMPRESA + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearEmpresa(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la empresa " + id, e);
        }
    }

    /** Empresa por nombre dentro de una rama (clave de negocio, la del UNIQUE por rama). */
    public Optional<Empresa> porNombreYRama(String nombre, String rama) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_EMPRESA + " WHERE nombre = ? AND rama = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, rama);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearEmpresa(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la empresa " + nombre + " (" + rama + ")", e);
        }
    }

    /** Empresa a la que pertenece el jugador, si está en alguna (JOIN con {@code empresa_miembros}). */
    public Optional<Empresa> deMiembro(long discordId) {
        String sql = "SELECT e.id, e.rama, e.dueno_discord_id, e.nombre, e.nivel, e.bote, e.creada "
                + "FROM empresas e JOIN empresa_miembros m ON m.empresa_id = e.id "
                + "WHERE m.discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearEmpresa(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la empresa de " + discordId, e);
        }
    }

    /** Miembros de una empresa, ordenados por antigüedad de alta. */
    public List<MiembroEmpresa> miembros(long empresaId) {
        String sql = "SELECT empresa_id, discord_id, rango, se_unio FROM empresa_miembros "
                + "WHERE empresa_id = ? ORDER BY se_unio ASC, discord_id ASC";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MiembroEmpresa> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearMiembro(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando miembros de la empresa " + empresaId, e);
        }
    }

    /** Empresas de una rama, ordenadas por antigüedad. */
    public List<Empresa> deRama(String rama) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     SELECT_EMPRESA + " WHERE rama = ? ORDER BY creada ASC, id ASC")) {
            ps.setString(1, rama);
            try (ResultSet rs = ps.executeQuery()) {
                List<Empresa> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearEmpresa(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando empresas de la rama " + rama, e);
        }
    }

    /** Disuelve la empresa. El {@code ON DELETE CASCADE} limpia sus miembros y pendientes. */
    public void disolver(long empresaId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM empresas WHERE id = ?")) {
            ps.setLong(1, empresaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error disolviendo la empresa " + empresaId, e);
        }
    }

    /**
     * Añade un miembro a una empresa con el rango dado. Propaga la violación de {@code
     * uq_miembro_unico} si el jugador ya pertenece a otra empresa (la pertenencia es exclusiva).
     */
    public void anadirMiembro(long empresaId, long discordId, RangoEmpresa rango) {
        String sql = "INSERT INTO empresa_miembros (empresa_id, discord_id, rango) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            ps.setString(3, rango.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Error añadiendo a " + discordId + " a la empresa " + empresaId, e);
        }
    }

    /**
     * Crea un pendiente (invitación o solicitud) y devuelve su id. Propaga la violación de {@code
     * uq_pendiente_par} si ya hay un pendiente para ese par empresa/jugador.
     */
    public long crearPendiente(long empresaId, long discordId, TipoPendiente tipo, String motivo) {
        String sql = "INSERT INTO empresa_pendientes (empresa_id, discord_id, tipo, motivo) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, empresaId);
            ps.setLong(2, discordId);
            ps.setString(3, tipo.name());
            ps.setString(4, motivo);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Error creando pendiente de " + discordId + " en la empresa " + empresaId, e);
        }
    }

    /** Pendiente por id. */
    public Optional<Pendiente> pendiente(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PENDIENTE + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearPendiente(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el pendiente " + id, e);
        }
    }

    /** Pendientes de una empresa de un tipo concreto (p. ej. las solicitudes que ve el dueño). */
    public List<Pendiente> pendientesDe(long empresaId, TipoPendiente tipo) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     SELECT_PENDIENTE + " WHERE empresa_id = ? AND tipo = ? ORDER BY creada ASC, id ASC")) {
            ps.setLong(1, empresaId);
            ps.setString(2, tipo.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Pendiente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearPendiente(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando pendientes de la empresa " + empresaId, e);
        }
    }

    /** Invitaciones dirigidas a un jugador (las que ve para aceptar/rechazar). */
    public List<Pendiente> invitacionesA(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     SELECT_PENDIENTE + " WHERE discord_id = ? AND tipo = ? ORDER BY creada ASC, id ASC")) {
            ps.setLong(1, discordId);
            ps.setString(2, TipoPendiente.INVITACION.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Pendiente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearPendiente(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando invitaciones de " + discordId, e);
        }
    }

    /** Borra un pendiente (al aceptar/rechazar/aprobar). */
    public void borrarPendiente(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM empresa_pendientes WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error borrando el pendiente " + id, e);
        }
    }

    private static final String SELECT_EMPRESA =
            "SELECT id, rama, dueno_discord_id, nombre, nivel, bote, creada FROM empresas";

    private static final String SELECT_PENDIENTE =
            "SELECT id, empresa_id, discord_id, tipo, motivo, creada FROM empresa_pendientes";

    private static Empresa mapearEmpresa(ResultSet rs) throws SQLException {
        return new Empresa(
                rs.getLong("id"),
                rs.getString("rama"),
                rs.getLong("dueno_discord_id"),
                rs.getString("nombre"),
                rs.getInt("nivel"),
                rs.getLong("bote"),
                rs.getTimestamp("creada").toInstant());
    }

    private static MiembroEmpresa mapearMiembro(ResultSet rs) throws SQLException {
        return new MiembroEmpresa(
                rs.getLong("empresa_id"),
                rs.getLong("discord_id"),
                RangoEmpresa.valueOf(rs.getString("rango")),
                rs.getTimestamp("se_unio").toInstant());
    }

    private static Pendiente mapearPendiente(ResultSet rs) throws SQLException {
        return new Pendiente(
                rs.getLong("id"),
                rs.getLong("empresa_id"),
                rs.getLong("discord_id"),
                TipoPendiente.valueOf(rs.getString("tipo")),
                rs.getString("motivo"),
                rs.getTimestamp("creada").toInstant());
    }
}
