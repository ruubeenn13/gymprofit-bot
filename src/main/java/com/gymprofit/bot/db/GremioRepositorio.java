package com.gymprofit.bot.db;

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
 * Repositorio JDBC de gremios ({@code gremios}, {@code gremio_miembros}). Un jugador pertenece a lo
 * sumo a un gremio (PK por {@code discord_id} en miembros).
 */
public final class GremioRepositorio {

    private final DataSource dataSource;

    public GremioRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Crea un gremio (sin miembros aún) y devuelve su id. */
    public long crear(String nombre, long dueno) {
        String sql = "INSERT INTO gremios (nombre, dueno) VALUES (?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setLong(2, dueno);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el gremio " + nombre, e);
        }
    }

    /** ¿Existe ya un gremio con ese nombre? */
    public boolean existeNombre(String nombre) {
        return contar("SELECT COUNT(*) FROM gremios WHERE nombre = ?", nombre) > 0;
    }

    /** Gremio al que pertenece el jugador, si tiene. */
    public Optional<Gremio> gremioDe(long discordId) {
        String sql = "SELECT g.id, g.nombre, g.dueno, g.canal_id FROM gremios g "
                + "JOIN gremio_miembros m ON m.gremio_id = g.id WHERE m.discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el gremio de " + discordId, e);
        }
    }

    /** Ids de los miembros de un gremio. */
    public List<Long> miembros(long gremioId) {
        String sql = "SELECT discord_id FROM gremio_miembros WHERE gremio_id = ? ORDER BY discord_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, gremioId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando miembros del gremio " + gremioId, e);
        }
    }

    /** Nº de miembros de un gremio. */
    public int contarMiembros(long gremioId) {
        return contarLong("SELECT COUNT(*) FROM gremio_miembros WHERE gremio_id = ?", gremioId);
    }

    /** Añade un miembro (idempotente). */
    public void anadirMiembro(long gremioId, long discordId) {
        String sql = "INSERT IGNORE INTO gremio_miembros (discord_id, gremio_id) VALUES (?, ?)";
        ejecutar(sql, ps -> {
            ps.setLong(1, discordId);
            ps.setLong(2, gremioId);
        });
    }

    /** Quita a un miembro de su gremio. */
    public void quitarMiembro(long discordId) {
        ejecutar("DELETE FROM gremio_miembros WHERE discord_id = ?", ps -> ps.setLong(1, discordId));
    }

    /** Fija el id del canal del gremio. */
    public void fijarCanal(long gremioId, long canalId) {
        ejecutar("UPDATE gremios SET canal_id = ? WHERE id = ?", ps -> {
            ps.setLong(1, canalId);
            ps.setLong(2, gremioId);
        });
    }

    /** Elimina un gremio (los miembros caen por la FK ON DELETE CASCADE). */
    public void eliminar(long gremioId) {
        ejecutar("DELETE FROM gremios WHERE id = ?", ps -> ps.setLong(1, gremioId));
    }

    private interface Param {
        void set(PreparedStatement ps) throws SQLException;
    }

    private void ejecutar(String sql, Param p) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            p.set(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error en operación de gremio", e);
        }
    }

    private int contar(String sql, String arg) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando gremios", e);
        }
    }

    private int contarLong(String sql, long arg) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando miembros", e);
        }
    }

    private static Gremio mapear(ResultSet rs) throws SQLException {
        long canal = rs.getLong("canal_id");
        return new Gremio(rs.getLong("id"), rs.getString("nombre"), rs.getLong("dueno"),
                rs.wasNull() ? null : canal);
    }
}
