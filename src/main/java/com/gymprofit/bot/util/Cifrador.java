package com.gymprofit.bot.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra y descifra texto libre con posible dato personal (motivos de sanción, apodos previos,
 * transcripts) con <b>AES-256-GCM</b>. Cumple el enfoque de protección de datos del bot: solo se
 * cifra el texto libre; los IDs y numéricos van en claro para poder consultar y paginar (ver
 * ADR-009 y el spec de moderación/RGPD).
 *
 * <p>La clave (32 bytes en base64) llega por la variable de entorno {@code BOT_CRYPTO_KEY}. Formato
 * de salida: {@code base64(iv(12) ‖ ciphertext ‖ tag(16))}. Si no hay clave configurada, el bot
 * arranca igual pero {@link #habilitado()} devuelve {@code false} y cifrar/descifrar lanzan (el
 * llamador decide degradar sin persistir el texto). <b>Perder la clave = no poder descifrar</b> los
 * motivos ya guardados.</p>
 */
public final class Cifrador {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int CLAVE_BYTES = 32; // AES-256

    private final SecretKeySpec clave; // null si no hay clave configurada
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * @param claveBase64 clave AES-256 (32 bytes) en base64, o vacío/{@code null} para deshabilitar
     * @throws IllegalArgumentException si la clave no decodifica a exactamente 32 bytes
     */
    public Cifrador(String claveBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            this.clave = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(claveBase64.trim());
        if (raw.length != CLAVE_BYTES) {
            throw new IllegalArgumentException(
                    "BOT_CRYPTO_KEY debe ser una clave de 32 bytes (AES-256) codificada en base64");
        }
        this.clave = new SecretKeySpec(raw, "AES");
    }

    /** {@code true} si hay clave y por tanto se puede cifrar/descifrar. */
    public boolean habilitado() {
        return clave != null;
    }

    /**
     * Cifra un texto. {@code null} → {@code null}.
     *
     * @throws IllegalStateException si no hay clave o falla el cifrado
     */
    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        exigirClave();
        try {
            byte[] iv = new byte[IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] salida = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, salida, 0, iv.length);
            System.arraycopy(ct, 0, salida, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(salida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el texto", e);
        }
    }

    /**
     * Descifra un texto producido por {@link #cifrar}. {@code null} → {@code null}.
     *
     * @throws IllegalStateException si no hay clave o el texto está corrupto/manipulado
     */
    public String descifrar(String cifrado) {
        if (cifrado == null) {
            return null;
        }
        exigirClave();
        try {
            byte[] todo = Base64.getDecoder().decode(cifrado);
            byte[] iv = Arrays.copyOfRange(todo, 0, IV_BYTES);
            byte[] ct = Arrays.copyOfRange(todo, IV_BYTES, todo.length);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el texto", e);
        }
    }

    private void exigirClave() {
        if (clave == null) {
            throw new IllegalStateException("BOT_CRYPTO_KEY no configurada: cifrado no disponible");
        }
    }

    /** Genera una clave AES-256 aleatoria en base64, para poblar {@code BOT_CRYPTO_KEY} una vez. */
    public static String generarClaveBase64() {
        byte[] clave = new byte[CLAVE_BYTES];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }
}
