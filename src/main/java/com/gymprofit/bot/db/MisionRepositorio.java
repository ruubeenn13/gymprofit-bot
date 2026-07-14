package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio JDBC del progreso de misiones ({@code mision_progreso}). El catálogo (metas,
 * recompensas) vive en código; aquí solo se lleva el contador de progreso por (jugador, misión).
 */
public final class MisionRepositorio {

    private final DataSource dataSource;

    public MisionRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Progreso de todas las misiones del jugador (id → progreso). */
    public Map<String, Integer> progreso(long discordId) {
        String sql = "SELECT mision_id, progreso FROM mision_progreso WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> m = new HashMap<>();
                while (rs.next()) {
                    m.put(rs.getString("mision_id"), rs.getInt("progreso"));
                }
                return m;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo misiones de " + discordId, e);
        }
    }

    /** Fija el progreso de una misión (upsert). Se usa para incrementar y para reiniciar a 0. */
    public void fijarProgreso(long discordId, String misionId, int progreso) {
        String sql = "INSERT INTO mision_progreso (discord_id, mision_id, progreso) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE progreso = VALUES(progreso)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, misionId);
            ps.setInt(3, progreso);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando misión de " + discordId, e);
        }
    }
}
