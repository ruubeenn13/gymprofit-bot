package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositorio JDBC del banco de frases (tabla {@code frases}). Solo lectura: las frases se
 * siembran por migración (V2), nunca desde el bot.
 */
public final class FraseRepositorio {

    private final DataSource dataSource;

    public FraseRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Una frase al azar del banco. {@code ORDER BY RAND()} es O(n) pero el banco tiene ~32
     * filas: más simple que un offset aleatorio y de sobra para un comando con cooldown.
     *
     * @return la frase, o vacío si el banco estuviese vacío
     */
    public Optional<Frase> aleatoria() {
        String sql = "SELECT id, texto_es, texto_en, autor FROM frases ORDER BY RAND() LIMIT 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new Frase(rs.getLong("id"), rs.getString("texto_es"),
                    rs.getString("texto_en"), rs.getString("autor")));
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo una frase aleatoria", e);
        }
    }
}
