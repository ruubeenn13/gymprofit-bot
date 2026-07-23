-- ----------------------------------------------------------------------------
-- Economía de empresas (F3). El bote y el nivel de la empresa ya existen (V27); aquí solo se añade una
-- carga extra a las propuestas de gestión: el puesto destino del ascenso patrocinado (ASCENSO). El
-- resto de tipos (CAMBIAR_RANGO, SACAR, DESPEDIR) no la usan y la dejan a NULL.
-- ----------------------------------------------------------------------------
ALTER TABLE empresa_propuestas
    ADD COLUMN dato VARCHAR(32) NULL COMMENT 'Carga extra: puesto destino en ASCENSO, NULL en el resto';
