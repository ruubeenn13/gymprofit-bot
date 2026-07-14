package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CombateService;
import com.gymprofit.bot.services.EconomiaService;
import com.gymprofit.bot.services.EconomiaService.Perfil;
import com.gymprofit.bot.services.NivelCalculadora;
import com.gymprofit.bot.services.Rango;
import com.gymprofit.bot.services.RangoService;
import com.gymprofit.bot.util.Barras;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /rank}: tarjeta de progresión (rango, nivel, barra de XP, poder y saldo); ajusta el rango. */
public final class RankComando implements Comando {

    private static final String NOMBRE = "rank";
    private static final int SEGMENTOS = 12;

    private final EconomiaService economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final RangoService rangos;

    public RankComando(EconomiaService economia, UsuarioDiscordRepositorio usuarios,
                       RangoService rangos) {
        this.economia = economia;
        this.usuarios = usuarios;
        this.rangos = rangos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.rank.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.rank.opcion.usuario"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.rank.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.rank.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.rank.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Member objetivo = evento.getOption("usuario") != null
                ? evento.getOption("usuario").getAsMember() : evento.getMember();
        if (objetivo == null) {
            evento.reply(Messages.get(locale, "comando.error.generico")).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(false).queue();
        long id = objetivo.getIdLong();
        Perfil p = economia.perfil(id);
        UsuarioDiscord u = usuarios.buscar(id).orElseGet(() -> usuarios.obtenerOCrear(id));

        // Barra de XP dentro del nivel actual.
        int base = NivelCalculadora.xpParaNivel(u.nivel());
        int siguiente = NivelCalculadora.xpParaNivel(u.nivel() + 1);
        int enNivel = Math.max(0, u.xp() - base);
        int faltan = Math.max(0, siguiente - u.xp());
        String barra = Barras.progreso(enNivel, Math.max(1, siguiente - base), SEGMENTOS);

        String desc = Messages.get(locale, "rank.cuerpo",
                Rango.para(u.nivel()).rolNombre(), u.nivel(), barra, faltan,
                p.coins(), CombateService.poderCombate(p.personaje()),
                p.personaje().fuerza(), p.personaje().resistencia(), p.personaje().carisma(),
                p.personaje().estudios());

        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                        Messages.get(locale, "rank.titulo", objetivo.getEffectiveName()), desc)
                .setThumbnail(objetivo.getEffectiveAvatarUrl()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();

        // Auto-corrige el rol de rango del objetivo.
        rangos.sincronizar(objetivo.getGuild(), objetivo, u.nivel());
    }
}
