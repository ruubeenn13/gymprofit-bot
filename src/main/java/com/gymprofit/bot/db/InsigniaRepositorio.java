package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Repositorio JDBC de las insignias ganadas ({@code insignias_ganadas}). El catálogo vive en código;
 * aquí solo se guarda qué insignias ha desbloqueado cada jugador.
 */
public final class InsigniaRepositorio {

    private final DataSource dataSource;

    public InsigniaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Ids de las insignias que el jugador ya tiene. */
    public Set<String> ganadas(long discordId) {
        String sql = "SELECT insignia_id FROM insignias_ganadas WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> s = new HashSet<>();
                while (rs.next()) {
                    s.add(rs.getString("insignia_id"));
                }
                return s;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo insignias de " + discordId, e);
        }
    }

    /** Concede una insignia (idempotente). */
    public void otorgar(long discordId, String insigniaId) {
        String sql = "INSERT IGNORE INTO insignias_ganadas (discord_id, insignia_id) VALUES (?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, insigniaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error otorgando insignia a " + discordId, e);
        }
    }
}
