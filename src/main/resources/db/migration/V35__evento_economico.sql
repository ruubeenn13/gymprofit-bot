-- V35: clima económico global (F5 eventos). Fila ÚNICA (id siempre 1): describe el evento activo.
-- Tabla vacía = sin evento (estado neutro). No hay FKs: es estado global del servidor, no de una entidad.
CREATE TABLE evento_economico (
    id     TINYINT     NOT NULL DEFAULT 1 COMMENT 'Fila única: siempre 1',
    tipo   VARCHAR(32) NOT NULL COMMENT 'EventoEconomico.name()',
    inicio TIMESTAMP   NOT NULL COMMENT 'Cuándo empezó el evento activo',
    fin    TIMESTAMP   NOT NULL COMMENT 'Cuándo caduca (inicio + DURACION)',
    PRIMARY KEY (id)
);
