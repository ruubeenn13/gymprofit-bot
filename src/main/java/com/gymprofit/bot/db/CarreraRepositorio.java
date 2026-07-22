package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a la tabla {@code carreras}: tier alcanzado por usuario y rama (ascensos de carrera).
 * Sin fila = el usuario está en el tier de entrada de la rama; la fila solo aparece al ascender.
 */
public final class CarreraRepositorio {

    private final DataSource dataSource;

    public CarreraRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Tier alcanzado en la rama, o 0 si nunca ha ascendido en ella (el service aplica la entrada). */
    public int tierAlcanzado(long discordId, String rama) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT tier_alcanzado FROM carreras WHERE discord_id = ? AND rama = ?")) {
            ps.setLong(1, discordId);
            ps.setString(2, rama);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la carrera de " + discordId, e);
        }
    }

    /**
     * Fija el tier alcanzado en la rama. {@code GREATEST} hace la operación idempotente y evita
     * regresiones: un tier alcanzado nunca baja. {@code ON DUPLICATE KEY UPDATE} es seguro aquí
     * porque la PK compuesta es la <b>única</b> clave de la tabla (la trampa de
     * {@code pasivos_equipados} era tener dos claves únicas).
     */
    public void fijarTier(long discordId, String rama, int tier) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO carreras (discord_id, rama, tier_alcanzado) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE "
                             + "tier_alcanzado = GREATEST(tier_alcanzado, VALUES(tier_alcanzado))")) {
            ps.setLong(1, discordId);
            ps.setString(2, rama);
            ps.setInt(3, tier);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando la carrera de " + discordId, e);
        }
    }
}
