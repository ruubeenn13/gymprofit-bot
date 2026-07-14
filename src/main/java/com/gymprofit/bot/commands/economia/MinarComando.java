package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MineriaService;
import com.gymprofit.bot.services.MineriaService.Resultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.Map;

/** {@code /minar}: extrae minerales con tu mejor pico (gasta energía, cooldown, sube minería). */
public final class MinarComando implements Comando {

    private static final String NOMBRE = "minar";

    private final MineriaService mineria;

    public MinarComando(MineriaService mineria) {
        this.mineria = mineria;
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
        evento.deferReply(false).queue();
        Resultado r = mineria.minar(evento.getUser().getIdLong());

        String mensaje = switch (r.estado()) {
            case OK -> mensajeExito(locale, r);
            case SIN_PICO -> Messages.get(locale, "minar.sinpico");
            case PICO_ROTO -> Messages.get(locale, "minar.picoroto");
            case SIN_ENERGIA -> Messages.get(locale, "minar.sinenergia", r.detalle());
            case EN_COOLDOWN -> Messages.get(locale, "minar.cooldown", r.detalle());
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
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
        return Messages.get(locale, "minar.ok", botin.toString().strip(), r.nivelNuevo(),
                pico, r.durabilidad(), r.durabilidadMax());
    }
}
