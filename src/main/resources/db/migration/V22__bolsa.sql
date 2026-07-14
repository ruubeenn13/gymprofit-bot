-- ----------------------------------------------------------------------------
-- Bolsa ficticia (extra) — precios dinámicos y carteras.
--
-- Acciones inventadas cuyo precio se mueve solo (job de random walk por volatilidad + eventos de
-- mercado). El catálogo (nombre, emoji, volatilidad) vive en código (services/Acciones); aquí van el
-- precio actual y el anterior (para la tendencia ↑↓) y las posiciones de cada jugador. Todo es
-- ficción: la moneda no es real ni convertible. Se siembran los precios iniciales.
-- ----------------------------------------------------------------------------
CREATE TABLE acciones (
    id           VARCHAR(20) NOT NULL COMMENT 'Ticker (catálogo services/Acciones)',
    precio       BIGINT      NOT NULL COMMENT 'Precio actual (coins)',
    precio_previo BIGINT     NOT NULL COMMENT 'Precio anterior (para la tendencia)',
    PRIMARY KEY (id)
) COMMENT 'Precios de las acciones de la bolsa ficticia.';

INSERT INTO acciones (id, precio, precio_previo) VALUES
    ('gymx', 200, 200),
    ('protn', 80, 80),
    ('techv', 150, 150),
    ('crpto', 50, 50),
    ('moonx', 30, 30),
    ('banko', 300, 300),
    ('gamez', 120, 120),
    ('memes', 10, 10);

CREATE TABLE cartera (
    discord_id BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    accion_id  VARCHAR(20) NOT NULL COMMENT 'Ticker de la acción',
    cantidad   BIGINT      NOT NULL COMMENT 'Nº de acciones que posee',
    coste      BIGINT      NOT NULL COMMENT 'Coste total invertido (para el P/L)',
    PRIMARY KEY (discord_id, accion_id),
    CONSTRAINT fk_cartera_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Posiciones (holdings) de los jugadores en la bolsa.';
