-- V32: contador de impagos consecutivos de la cuota semanal (F5b impuestos). 0 = al dia; a
-- MOROSIDAD_MAX (3) impagos seguidos la empresa quiebra. Se resetea a 0 en cuanto paga una cuota.
ALTER TABLE empresas ADD COLUMN impagos INT NOT NULL DEFAULT 0 COMMENT 'Impagos consecutivos de la cuota semanal (F5b); 0 = al dia, quiebra a los 3';
