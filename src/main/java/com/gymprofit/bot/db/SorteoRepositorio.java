package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de {@link Sorteo} (tabla {@code sorteos}). Sigue el patrón del resto de repos:
 * conexión por operación y consultas parametrizadas.
 */
public final class SorteoRepositorio {

    private final DataSource dataSource;

    public SorteoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserta un sorteo y devuelve su id generado. */
    public long insertar(long guildId, long canalId, long mensajeId, String premio,
                         int numGanadores, long creadorId, long finEpoch) {
        String sql = "INSERT INTO sorteos "
                + "(guild_id, canal_id, mensaje_id, premio, num_ganadores, creador_id, fin_epoch) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, guildId);
            ps.setLong(2, canalId);
            ps.setLong(3, mensajeId);
            ps.setString(4, premio);
            ps.setInt(5, numGanadores);
            ps.setLong(6, creadorId);
            ps.setLong(7, finEpoch);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error insertando sorteo en " + guildId, e);
        }
    }

    /** Sorteos activos cuya hora de cierre ya pasó ({@code fin_epoch <= ahora}). */
    public List<Sorteo> vencidos(long ahoraEpoch) {
        String sql = "SELECT id, guild_id, canal_id, mensaje_id, premio, num_ganadores, creador_id, "
                + "fin_epoch, activo FROM sorteos WHERE activo = TRUE AND fin_epoch <= ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, ahoraEpoch);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sorteo> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando sorteos vencidos", e);
        }
    }

    /** Marca un sorteo como resuelto (inactivo). */
    public void cerrar(long id) {
        String sql = "UPDATE sorteos SET activo = FALSE WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error cerrando el sorteo " + id, e);
        }
    }

    private static Sorteo mapear(ResultSet rs) throws SQLException {
        return new Sorteo(
                rs.getLong("id"),
                rs.getLong("guild_id"),
                rs.getLong("canal_id"),
                rs.getLong("mensaje_id"),
                rs.getString("premio"),
                rs.getInt("num_ganadores"),
                rs.getLong("creador_id"),
                rs.getLong("fin_epoch"),
                rs.getBoolean("activo"));
    }
}
