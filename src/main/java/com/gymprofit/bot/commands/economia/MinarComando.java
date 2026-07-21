package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.ReintentoRegistro;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MineriaService;
import com.gymprofit.bot.services.MineriaService.Resultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** {@code /minar}: extrae minerales con tu mejor pico (gasta energía, cooldown, sube minería). */
public final class MinarComando implements Comando {

    private static final String NOMBRE = "minar";

    private final MineriaService mineria;
    private final ReintentoRegistro reintentos;

    public MinarComando(MineriaService mineria, ReintentoRegistro reintentos) {
        this.mineria = mineria;
        this.reintentos = reintentos;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.minar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.minar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.minar.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long userId = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        evento.getHook().sendMessage(minar(userId, locale)).queue();
    }

    /**
     * Ejecuta un minado y devuelve el mensaje ya montado. Devuelve datos en vez de enviarlos porque
     * lo reutiliza el <b>reintento</b>: si estabas dormido, esta misma llamada se relanza al
     * despertar desde el botón (ver {@link ReintentoRegistro}).
     */
    private MessageCreateData minar(long userId, Locale locale) {
        Resultado r = mineria.minar(userId);

        // Dormido: embed con botones (seguir durmiendo / despertar) en vez de un aviso de texto, y
        // se guarda el minado para relanzarlo si decide levantarse.
        if (r.estado() == MineriaService.Estado.DORMIDO) {
            reintentos.guardar(userId, Instant.now(), loc -> minar(userId, loc));
            return new MessageCreateBuilder()
                    .setEmbeds(DescansoComando.embedBloqueado(locale))
                    .setComponents(DescansoComando.botonesBloqueado(locale, userId))
                    .build();
        }
        String mensaje = switch (r.estado()) {
            case OK -> mensajeExito(locale, r);
            case SIN_PICO -> Messages.get(locale, "minar.sinpico");
            case PICO_ROTO -> Messages.get(locale, "minar.picoroto");
            case SIN_ENERGIA -> Messages.get(locale, "minar.sinenergia", r.detalle());
            case EN_COOLDOWN -> Messages.get(locale, "minar.cooldown", r.detalle());
            // Inalcanzable: DORMIDO sale por el return de arriba (necesita botones, no solo texto).
            case DORMIDO -> throw new IllegalStateException("DORMIDO ya tratado");
        };
        return new MessageCreateBuilder()
                .setEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).build();
    }

    private static String mensajeExito(Locale locale, Resultado r) {
        StringBuilder botin = new StringBuilder();
        for (Map.Entry<String, Integer> e : r.minerales().entrySet()) {
            String emoji = Items.porId(e.getKey()).map(Items::emoji).orElse("🪨");
            botin.append(emoji).append(' ').append(Messages.get(locale, "item." + e.getKey()))
                    .append(" ×").append(e.getValue()).append('\n');
        }
        String pico = Items.porId(r.picoId()).map(i -> i.emoji() + " "
                + Messages.get(locale, "item." + i.id())).orElse(r.picoId());
        StringBuilder cuerpo = new StringBuilder(Messages.get(locale, "minar.ok",
                botin.toString().strip(), r.nivelNuevo(), pico, r.durabilidad(),
                r.durabilidadMax()));
        if (r.durabilidadAhorrada()) {
            // Si no se dice, el jugador no se entera de que su pasivo está funcionando.
            cuerpo.append('\n').append(Messages.get(locale, "minar.durabilidad.ahorrada"));
        }
        return cuerpo.toString();
    }
}
