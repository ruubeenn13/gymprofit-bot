package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repositorio JDBC del estado de minería ({@code mineria}). {@link #obtenerOCrear} garantiza la fila
 * (requiere que exista antes {@code usuarios_discord}, por la FK). {@link #registrarMinado} sube el
 * nivel y marca el cooldown en una sola operación atómica.
 */
public final class MineriaRepositorio {

    private final DataSource dataSource;

    public MineriaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Estado de minería, creándolo con valores por defecto si no existe. */
    public MineriaEstado obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO mineria (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el estado de minería de " + discordId, e);
        }
        String sql = "SELECT discord_id, nivel_mineria, ultimo_minado FROM mineria WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("El estado de minería " + discordId
                            + " no existe tras crearlo", null);
                }
                java.sql.Timestamp um = rs.getTimestamp("ultimo_minado");
                return new MineriaEstado(rs.getLong("discord_id"), rs.getInt("nivel_mineria"),
                        um == null ? null : um.toInstant());
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando la minería de " + discordId, e);
        }
    }

    /** Registra un minado: sube el nivel en 1 y fija {@code ultimo_minado = NOW()}. */
    public void registrarMinado(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE mineria SET nivel_mineria = nivel_mineria + 1, ultimo_minado = NOW() "
                             + "WHERE discord_id = ?")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error registrando minado de " + discordId, e);
        }
    }
}
