-- V36: participaciones de empresa (F5 acciones/dividendos). Holdings: cuántas participaciones (1..100)
-- tiene cada jugador de cada empresa. La fila se borra al llegar a 0. FK a empresas ON DELETE CASCADE:
-- al disolver (quiebra F5b o manual) las participaciones desaparecen (los inversores pierden el capital).
CREATE TABLE empresa_acciones (
    empresa_id  BIGINT NOT NULL,
    discord_id  BIGINT NOT NULL,
    cantidad    INT    NOT NULL COMMENT 'Participaciones (1..100); la fila se borra al llegar a 0',
    PRIMARY KEY (empresa_id, discord_id),
    CONSTRAINT fk_acc_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id) ON DELETE CASCADE,
    CONSTRAINT fk_acc_usuario FOREIGN KEY (discord_id) REFERENCES usuarios_discord(discord_id) ON DELETE CASCADE
);
