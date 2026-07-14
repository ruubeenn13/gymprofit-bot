package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repositorio JDBC de las cuentas de banco ({@code banco}). {@link #obtenerOCrear} garantiza la fila
 * (requiere que exista antes {@code usuarios_discord}). {@link #guardar} persiste saldo, préstamo e
 * interés de una vez (el service lee, muta y guarda).
 */
public final class BancoRepositorio {

    private final DataSource dataSource;

    public BancoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Cuenta del jugador, creándola vacía si no existe. */
    public BancoCuenta obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO banco (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando la cuenta de banco de " + discordId, e);
        }
        String sql = "SELECT discord_id, saldo, prestamo, ultimo_interes FROM banco WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("La cuenta " + discordId + " no existe tras crearla", null);
                }
                Date d = rs.getDate("ultimo_interes");
                return new BancoCuenta(rs.getLong("discord_id"), rs.getLong("saldo"),
                        rs.getLong("prestamo"), d == null ? null : d.toLocalDate());
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando el banco de " + discordId, e);
        }
    }

    /** Persiste el estado completo de la cuenta. */
    public void guardar(BancoCuenta cuenta) {
        String sql = "UPDATE banco SET saldo = ?, prestamo = ?, ultimo_interes = ? WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, cuenta.saldo());
            ps.setLong(2, cuenta.prestamo());
            ps.setDate(3, cuenta.ultimoInteres() == null ? null : Date.valueOf(cuenta.ultimoInteres()));
            ps.setLong(4, cuenta.discordId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el banco de " + cuenta.discordId(), e);
        }
    }
}
