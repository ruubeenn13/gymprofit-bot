package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.CredencialesDTO;
import com.gymprofit.bot.api.dtos.RefreshDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Endpoints de autenticación de la API. Se consumen con un Retrofit SIN interceptor de
 * Authorization (romperíamos el login con un token caducado) — ver {@link ApiClient}.
 */
public interface AuthApi {

    @POST("auth/login")
    Call<TokenDTO> login(@Body CredencialesDTO credenciales);

    @POST("auth/refresh")
    Call<TokenDTO> refresh(@Body RefreshDTO refresh);
}
