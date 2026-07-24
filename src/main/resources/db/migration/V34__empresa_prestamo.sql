-- V34: prestamo empresarial (F5d). deuda = lo que queda por devolver (0 = sin prestamo); cuota_prestamo
-- = cuota semanal que cobra el job de F5b junto al impuesto. Uno a la vez (deuda>0 bloquea otro).
ALTER TABLE empresas
    ADD COLUMN deuda           BIGINT NOT NULL DEFAULT 0
        COMMENT 'Lo que queda por devolver del prestamo (F5d); 0 = sin prestamo activo',
    ADD COLUMN cuota_prestamo  BIGINT NOT NULL DEFAULT 0
        COMMENT 'Cuota semanal del prestamo que cobra el job de F5b junto al impuesto (F5d); 0 = sin prestamo';
