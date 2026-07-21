package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

/**
 * Catálogo de ejercicios de la API. El idioma va por petición en {@code Accept-Language}
 * ({@code es}/{@code en}); los mappers de la API devuelven los textos ya localizados.
 */
public interface EjerciciosApi {

    /** Búsqueda paginada del catálogo (q, grupo y dificultad opcionales: null = sin filtro). */
    @GET("ejercicios/buscar")
    Call<PaginaDTO<EjercicioDTO>> buscar(@Query("q") String q,
                                         @Query("grupoMuscular") String grupoMuscular,
                                         @Query("dificultad") String dificultad,
                                         @Query("page") int page,
                                         @Query("size") int size,
                                         @Header("Accept-Language") String idioma);

    /** Ficha completa de un ejercicio. */
    @GET("ejercicios/{id}")
    Call<EjercicioDTO> porId(@Path("id") int id, @Header("Accept-Language") String idioma);

    /** Catálogo completo (lo usa el sorteo diario para conocer todos los ids). */
    @GET("ejercicios")
    Call<List<EjercicioDTO>> listarTodos(@Header("Accept-Language") String idioma);
}
