-- ----------------------------------------------------------------------------
-- Banco (F-ECO-4c) — ahorro con interés y préstamos.
--
-- El saldo del banco está separado del monedero (usuarios_discord.coins): el ahorro genera un
-- pequeño interés diario (con tope) y los préstamos permiten adelantar coins con una comisión al
-- devolverlos (sumidero). El interés se aplica de forma perezosa (por días transcurridos desde
-- ultimo_interes) al interactuar con el banco, para no depender de un job 24/7.
-- ----------------------------------------------------------------------------
CREATE TABLE banco (
    discord_id     BIGINT NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    saldo          BIGINT NOT NULL DEFAULT 0 COMMENT 'Ahorro en el banco',
    prestamo       BIGINT NOT NULL DEFAULT 0 COMMENT 'Deuda pendiente del préstamo',
    ultimo_interes DATE   NULL COMMENT 'Último día en que se aplicó interés',
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_banco_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Cuentas de banco (ahorro con interés y préstamos).';
