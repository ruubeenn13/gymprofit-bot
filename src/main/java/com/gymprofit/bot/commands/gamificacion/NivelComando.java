package com.gymprofit.bot.commands.gamificacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.NivelCalculadora;
import com.gymprofit.bot.util.Barras;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /nivel [usuario]}: muestra el nivel, la XP y el progreso hacia el siguiente nivel del
 * usuario indicado (o de quien invoca si no se indica). Solo lee BD, así que no necesita cooldown.
 */
public final class NivelComando implements Comando {

    private static final String NOMBRE = "nivel";
    private static final String OPCION_USUARIO = "usuario";

    private final UsuarioDiscordRepositorio repositorio;

    public NivelComando(UsuarioDiscordRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, OPCION_USUARIO,
                Messages.get(Messages.ES, "comando.nivel.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.nivel.opcion.usuario"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.nivel.opcion.usuario"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_UK,
                        Messages.get(Messages.EN, "comando.nivel.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.nivel.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.nivel.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.nivel.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_UK,
                        Messages.get(Messages.EN, "comando.nivel.descripcion"))
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption(OPCION_USUARIO, evento.getUser(),
                opcion -> opcion.getAsUser());

        // Si no tiene fila aún, se muestra una tarjeta a cero sin crearla en BD.
        UsuarioDiscord datos = repositorio.buscar(objetivo.getIdLong())
                .orElse(new UsuarioDiscord(objetivo.getIdLong(), 0, 0, 0, 0, null, "es", false));

        int nivel = NivelCalculadora.nivelDeXp(datos.xp());
        int baseNivel = NivelCalculadora.xpParaNivel(nivel);
        int siguienteNivel = NivelCalculadora.xpParaNivel(nivel + 1);
        int progreso = datos.xp() - baseNivel;
        int objetivoXp = siguienteNivel - baseNivel;

        String valorProgreso = Barras.progreso(progreso, objetivoXp, 12) + "\n"
                + Messages.get(locale, "comando.nivel.valor.progreso", progreso, objetivoXp, nivel + 1);

        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                        Messages.get(locale, "comando.nivel.titulo", objetivo.getEffectiveName()))
                .setThumbnail(objetivo.getEffectiveAvatarUrl())
                .addField(Messages.get(locale, "comando.nivel.campo.nivel"),
                        "**" + nivel + "**", true)
                .addField(Messages.get(locale, "comando.nivel.campo.xp"),
                        datos.xp() + " XP", true)
                .addBlankField(true)
                .addField(Messages.get(locale, "comando.nivel.campo.progreso"), valorProgreso, false)
                .build();

        evento.replyEmbeds(embed).queue();
    }
}
