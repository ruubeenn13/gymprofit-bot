package com.gymprofit.bot.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que los records de {@code api/dtos} mapean el JSON real de la API GymProFit
 * (mismos nombres de campo que sus DTOs de Spring; Gson soporta records desde 2.10).
 */
class DtosMapeoTest {

    private final Gson gson = new Gson();

    @Test
    void mapeaTokenDTO() {
        TokenDTO t = gson.fromJson(
                "{\"token\":\"jwt\",\"refreshToken\":\"opaco\",\"username\":\"gymprobot\",\"roles\":[\"ADMIN\"]}",
                TokenDTO.class);
        assertEquals("jwt", t.token());
        assertEquals("opaco", t.refreshToken());
        assertEquals("ADMIN", t.roles().get(0));
    }

    @Test
    void mapeaPaginaDeEjercicios() {
        String json = """
                {"content":[{"id":7,"nombre":"Press banca","descripcion":"Empuje horizontal",
                "grupoMuscular":"PECHO","musculoPrimario":"Pectoral mayor","dificultad":"INTERMEDIO",
                "imagenUrl":"http://img/1.png","imagenUrl2":null,"instrucciones":"Baja la barra...",
                "caloriasQuemadas":8,"equipoNecesario":"Barra","activo":true}],
                "page":0,"size":8,"totalElements":873,"totalPages":110,"last":false}""";
        PaginaDTO<EjercicioDTO> p = gson.fromJson(json,
                new TypeToken<PaginaDTO<EjercicioDTO>>() { }.getType());
        assertEquals(873, p.totalElements());
        assertEquals(110, p.totalPages());
        EjercicioDTO e = p.content().get(0);
        assertEquals(7, e.id());
        assertEquals("PECHO", e.grupoMuscular());
        assertNull(e.imagenUrl2());
        assertTrue(e.activo());
    }
}
