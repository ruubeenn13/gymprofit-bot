package com.gymprofit.bot.config;

/**
 * Acceso centralizado a la configuración del bot. Todos los secretos y parámetros
 * llegan por <b>variables de entorno</b> (en local, vía archivo {@code .env} exportado
 * a mano; nunca se commitea, ver {@code GYMPROBOT_SPEC.md} §9).
 *
 * <p>Variables reconocidas: {@code DISCORD_TOKEN}, {@code DB_URL}, {@code DB_USER},
 * {@code DB_PASSWORD}, {@code GYMPROFIT_API_URL}, {@code BOT_SERVICE_USER},
 * {@code BOT_SERVICE_PASSWORD}, {@code BOT_CRYPTO_KEY}, {@code PORT}, {@code TZ}.</p>
 *
 * <p>Prohibido loggear el valor de los secretos.</p>
 */
public final class BotConfig {

    /** Versión del bot; se mantiene sincronizada con la {@code <version>} del pom. */
    private static final String VERSION = "0.1.0-SNAPSHOT";

    private BotConfig() {
    }

    /** Puerto del health server. Render inyecta {@code PORT}; por defecto 8080. */
    public static int port() {
        String raw = env("PORT", "8080");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    public static String discordToken() {
        return env("DISCORD_TOKEN", "");
    }

    public static String dbUrl() {
        return env("DB_URL", "");
    }

    public static String dbUser() {
        return env("DB_USER", "");
    }

    public static String dbPassword() {
        return env("DB_PASSWORD", "");
    }

    public static String apiUrl() {
        return env("GYMPROFIT_API_URL", "");
    }

    public static String botServiceUser() {
        return env("BOT_SERVICE_USER", "");
    }

    public static String botServicePassword() {
        return env("BOT_SERVICE_PASSWORD", "");
    }

    /**
     * Clave AES-256 (32 bytes en base64) para cifrar el texto libre con posible dato personal
     * (motivos de sanción, apodos previos). Vacío = cifrado deshabilitado (arranque degradado).
     * Generar una con {@code Cifrador.generarClaveBase64()} y guardarla a buen recaudo: perderla
     * impide descifrar lo ya guardado.
     */
    public static String cryptoKey() {
        return env("BOT_CRYPTO_KEY", "");
    }

    public static String version() {
        return VERSION;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
