package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Repositorio JDBC del progreso de mundos ({@code progreso_mundos}). Guarda qué mundos ha
 * <b>completado</b> cada jugador (jefe derrotado). El catálogo de mundos/monstruos vive en código
 * (services/{@code Mundos}, {@code Monstruos}); aquí solo persistimos el progreso.
 *
 * <p>En COMBAT-2 solo se consulta ({@link #completados}); la escritura ({@link #marcarJefeDerrotado})
 * queda lista para COMBAT-3, cuando una victoria contra un jefe marque el mundo como superado.</p>
 */
public final class MundoRepositorio {

    private final DataSource dataSource;

    public MundoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Ids de los mundos que el jugador ha completado (jefe derrotado). */
    public Set<String> completados(long discordId) {
        String sql = "SELECT mundo FROM progreso_mundos "
                + "WHERE discord_id = ? AND jefe_derrotado = TRUE";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> mundos = new HashSet<>();
                while (rs.next()) {
                    mundos.add(rs.getString("mundo"));
                }
                return mundos;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando progreso de mundos de " + discordId, e);
        }
    }

    /**
     * Marca un mundo como completado (jefe derrotado) para el jugador. Idempotente. Se usará en
     * COMBAT-3 al vencer al jefe del mundo. Requiere que exista la fila de {@code usuarios_discord}.
     */
    public void marcarJefeDerrotado(long discordId, String mundo) {
        String sql = "INSERT INTO progreso_mundos (discord_id, mundo, jefe_derrotado) "
                + "VALUES (?, ?, TRUE) ON DUPLICATE KEY UPDATE jefe_derrotado = TRUE";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, mundo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error marcando jefe derrotado (" + mundo + ") de "
                    + discordId, e);
        }
    }
}
