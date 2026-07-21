package com.gymprofit.bot.api;

import com.gymprofit.bot.api.dtos.CredencialesDTO;
import com.gymprofit.bot.api.dtos.RefreshDTO;
import com.gymprofit.bot.api.dtos.TokenDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.IOException;

/**
 * Ciclo de vida del access token de la cuenta de servicio (SPEC §4.1): login perezoso en la
 * primera petición, renovación <b>solo ante un 401</b> (nunca por reloj) y todo en memoria (el
 * free tier de Render reinicia el proceso; re-loguear al arrancar es barato).
 *
 * <p>Los métodos son {@code synchronized} a propósito: si tres comandos chocan con un 401 a la
 * vez, el primero renueva y los otros dos reutilizan el token nuevo comparándolo con el que les
 * caducó ({@link #renovar}). Las credenciales jamás se loguean.</p>
 */
public final class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);

    private final AuthApi authApi;
    private final String usuario;
    private final String password;

    private String token;
    private String refreshToken;

    public TokenManager(AuthApi authApi, String usuario, String password) {
        this.authApi = authApi;
        this.usuario = usuario;
        this.password = password;
    }

    /** Access token vigente, haciendo login la primera vez. */
    public synchronized String obtenerToken() {
        if (token == null) {
            login();
        }
        return token;
    }

    /**
     * Renueva el token que acaba de recibir un 401. Si el vigente ya no es ese, otro hilo se
     * adelantó y se devuelve sin tocar la red. Intenta refresh (si hay uno guardado); si el
     * refresh también está caducado, login completo.
     *
     * @param tokenCaducado el token con el que la petición recibió el 401
     */
    public synchronized String renovar(String tokenCaducado) {
        if (token != null && !token.equals(tokenCaducado)) {
            return token;
        }
        if (refreshToken == null) {
            // Sin refresh que ofrecer, el POST solo gastaría un viaje (y con Render dormido eso
            // son ~50 s) para recibir el 400 de rigor: se va directo al login.
            login();
            return token;
        }
        try {
            Response<TokenDTO> respuesta =
                    authApi.refresh(new RefreshDTO(refreshToken)).execute();
            if (respuesta.isSuccessful() && respuesta.body() != null) {
                guardar(respuesta.body());
                return token;
            }
            log.info("Refresh token rechazado ({}); reintentando con login completo.",
                    respuesta.code());
        } catch (IOException e) {
            log.warn("Fallo de red renovando el token; reintentando con login completo.", e);
        }
        login();
        return token;
    }

    private void login() {
        try {
            Response<TokenDTO> respuesta =
                    authApi.login(new CredencialesDTO(usuario, password)).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                throw new ApiException("Login de la cuenta de servicio rechazado: HTTP "
                        + respuesta.code());
            }
            guardar(respuesta.body());
        } catch (IOException e) {
            throw new ApiException("Fallo de red en el login de la cuenta de servicio", e);
        }
    }

    private void guardar(TokenDTO dto) {
        this.token = dto.token();
        this.refreshToken = dto.refreshToken();
    }
}
