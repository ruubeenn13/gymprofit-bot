-- V33: flag de "esta empresa contrata" (F5c bolsa de empleo). Opt-in: por defecto cerrada. La lista
-- /empleo solo muestra las que lo tienen activo, de la rama del que busca.
ALTER TABLE empresas ADD COLUMN contratando BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Abierta a solicitudes en la bolsa de empleo (F5c); false = cerrada';
