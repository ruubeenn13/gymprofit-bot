package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Operaciones de dinero sobre el monedero ({@code usuarios_discord.coins}) y el ledger
 * ({@code transacciones}). Todo movimiento es <b>atómico</b> (una transacción por operación) y
 * <b>nunca deja saldo negativo</b>: los gastos usan {@code UPDATE ... WHERE coins >= importe}.
 */
public final class EconomiaRepositorio {

    /** Recompensa base del daily y escalado por racha (conservador: progresión lenta). */
    public static final int DAILY_BASE = 20;
    public static final int DAILY_POR_RACHA = 5;
    public static final int DAILY_RACHA_TOPE = 10;

    /** Resultado de cobrar el daily. */
    public record ResultadoDaily(boolean cobrado, int recompensa, int racha) {
    }

    /** Recompensa del daily para una racha dada (escala poco y se topa: progresión lenta). */
    public static int recompensaDaily(int racha) {
        return DAILY_BASE + Math.min(racha, DAILY_RACHA_TOPE) * DAILY_POR_RACHA;
    }

    private final DataSource dataSource;

    public EconomiaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Saldo actual de coins del usuario. */
    public long saldo(long discordId) {
        String sql = "SELECT coins FROM usuarios_discord WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando el saldo de " + discordId, e);
        }
    }

    /** Ingresa {@code cantidad} coins (atómico + ledger). */
    public void ingresar(long discordId, long cantidad, String motivo) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                actualizarCoins(con, "UPDATE usuarios_discord SET coins = coins + ? WHERE discord_id = ?",
                        cantidad, discordId);
                registrar(con, discordId, cantidad, motivo);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error ingresando coins a " + discordId, e);
        }
    }

    /**
     * Gasta {@code cantidad} coins si hay saldo suficiente (atómico + ledger). Devuelve {@code false}
     * si no había saldo (no se toca nada).
     */
    public boolean gastar(long discordId, long cantidad, String motivo) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                int filas = actualizarCoins(con,
                        "UPDATE usuarios_discord SET coins = coins - ? WHERE discord_id = ? AND coins >= ?",
                        cantidad, discordId, cantidad);
                if (filas == 0) {
                    con.rollback();
                    return false;
                }
                registrar(con, discordId, -cantidad, motivo);
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error gastando coins de " + discordId, e);
        }
    }

    /**
     * Cobra el daily de {@code hoy}: si ya se cobró, no hace nada; si el último fue ayer, sube la
     * racha, si no, la reinicia. La recompensa escala (poco) con la racha. Atómico.
     */
    public ResultadoDaily cobrarDaily(long discordId, LocalDate hoy) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                int racha;
                LocalDate ultima;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT racha, ultima_racha_fecha FROM usuarios_discord "
                                + "WHERE discord_id = ? FOR UPDATE")) {
                    ps.setLong(1, discordId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return new ResultadoDaily(false, 0, 0);
                        }
                        racha = rs.getInt("racha");
                        Date d = rs.getDate("ultima_racha_fecha");
                        ultima = d == null ? null : d.toLocalDate();
                    }
                }
                if (hoy.equals(ultima)) {
                    con.rollback();
                    return new ResultadoDaily(false, 0, racha);
                }
                int nuevaRacha = hoy.minusDays(1).equals(ultima) ? racha + 1 : 1;
                int recompensa = recompensaDaily(nuevaRacha);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE usuarios_discord SET coins = coins + ?, racha = ?, "
                                + "ultima_racha_fecha = ? WHERE discord_id = ?")) {
                    ps.setLong(1, recompensa);
                    ps.setInt(2, nuevaRacha);
                    ps.setDate(3, Date.valueOf(hoy));
                    ps.setLong(4, discordId);
                    ps.executeUpdate();
                }
                registrar(con, discordId, recompensa, "daily");
                con.commit();
                return new ResultadoDaily(true, recompensa, nuevaRacha);
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error cobrando el daily de " + discordId, e);
        }
    }

    private static int actualizarCoins(Connection con, String sql, long cantidad, long discordId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, cantidad);
            ps.setLong(2, discordId);
            return ps.executeUpdate();
        }
    }

    private static int actualizarCoins(Connection con, String sql, long cantidad, long discordId,
                                       long minimo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, cantidad);
            ps.setLong(2, discordId);
            ps.setLong(3, minimo);
            return ps.executeUpdate();
        }
    }

    private static void registrar(Connection con, long discordId, long delta, String motivo)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO transacciones (discord_id, delta, motivo) VALUES (?, ?, ?)")) {
            ps.setLong(1, discordId);
            ps.setLong(2, delta);
            ps.setString(3, motivo);
            ps.executeUpdate();
        }
    }
}
