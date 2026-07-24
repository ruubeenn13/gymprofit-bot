-- V31: almacen de mercancia de la empresa (F5a produccion). La produce el trabajo de los miembros
-- (subproducto de /trabajo currar, con tope por nivel) y se vende con /empresa vender. NOT NULL DEFAULT 0.
ALTER TABLE empresas ADD COLUMN mercancia BIGINT NOT NULL DEFAULT 0 COMMENT 'Unidades de mercancia en el almacen (F5a); 0 al fundar, tope nivel*100';
