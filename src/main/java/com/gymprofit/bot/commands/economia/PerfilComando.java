package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CombateService;
import com.gymprofit.bot.services.EconomiaService;
import com.gymprofit.bot.services.EconomiaService.Perfil;
import com.gymprofit.bot.services.Encantamiento;
import com.gymprofit.bot.services.InsigniaService;
import com.gymprofit.bot.services.InsigniaService.Vista;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.PasivoService;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /perfil} con subcomandos (ver, balance, insignias). Consolida la ficha del jugador en un solo
 * comando para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>{@code ver} muestra el personaje (atributos, energía, salud, equipo y poder) y el saldo, tuyo o de
 * otro; {@code balance} solo el saldo de coins; {@code insignias} los logros conseguidos y los que
 * faltan, otorgando de paso los recién cumplidos.
 */
public final class PerfilComando implements Comando {

    private static final String NOMBRE = "perfil";

    private final EconomiaService economia;
    private final InsigniaService insignias;
    private final PasivoService pasivos;

    public PerfilComando(EconomiaService economia, InsigniaService insignias, PasivoService pasivos) {
        this.economia = economia;
        this.insignias = insignias;
        this.pasivos = pasivos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.perfil.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.perfil.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.perfil.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.perfil.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.perfil.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.perfil.descripcion").addOptions(usuario),
                        sub("balance", "comando.balance.descripcion"),
                        sub("insignias", "comando.insignias.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        switch (sub) {
            case "ver" -> ver(evento, locale);
            case "balance" -> balance(evento, locale);
            case "insignias" -> insignias(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        User objetivo = evento.getOption("usuario") != null
                ? evento.getOption("usuario").getAsUser() : evento.getUser();

        evento.deferReply(false).queue();
        Perfil p = economia.perfil(objetivo.getIdLong());
        String arma = armaTexto(locale, p.personaje());
        String armadura = nombreEquipo(locale, p.personaje().armadura());
        String desc = Messages.get(locale, "perfil.cuerpo",
                p.coins(), p.personaje().energia(), p.personaje().salud(),
                p.personaje().fuerza(), p.personaje().resistencia(), p.personaje().carisma(),
                CombateService.poderCombate(p.personaje()), arma, armadura, p.personaje().estudios());
        // Los pasivos solo se pintan si hay alguno: un perfil recién creado no debe llevar una
        // línea a ceros.
        String bonos = PasivosTexto.bonos(locale, pasivos.bonosDe(objetivo.getIdLong()));
        if (!bonos.isEmpty()) {
            desc += Messages.get(locale, "perfil.pasivos.linea", bonos);
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "perfil.titulo", objetivo.getName()), desc)
                .setThumbnail(objetivo.getEffectiveAvatarUrl()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void balance(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        long saldo = economia.saldo(evento.getUser().getIdLong());
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "balance.titulo"),
                Messages.get(locale, "balance.saldo", saldo)).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void insignias(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();

        List<Vista> vistas = insignias.listar(evento.getUser().getIdLong());
        long conseguidas = vistas.stream().filter(Vista::ganada).count();

        StringBuilder sb = new StringBuilder(
                Messages.get(locale, "insignias.intro", conseguidas, vistas.size())).append("\n\n");
        for (Vista v : vistas) {
            String estado = v.ganada() ? "✅" : "🔒";
            sb.append(estado).append(' ').append(v.insignia().emoji()).append(' ')
                    .append("**").append(Messages.get(locale, "insignia." + v.insignia().id()))
                    .append("** — ")
                    .append(Messages.get(locale, "insignia." + v.insignia().id() + ".desc"))
                    .append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.LOGRO, locale,
                Messages.get(locale, "insignias.titulo"), sb.toString()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    /** Arma equipada con su mejora: nombre + «+nivel» + emoji del encantamiento, o «—» si no hay. */
    private static String armaTexto(Locale locale, Personaje p) {
        if (p.arma() == null) {
            return Messages.get(locale, "perfil.sinequipo");
        }
        StringBuilder sb = new StringBuilder(nombreEquipo(locale, p.arma()));
        if (p.armaNivel() > 0) {
            sb.append(" **+").append(p.armaNivel()).append("**");
        }
        if (p.armaEncanto() != null) {
            Encantamiento.porId(p.armaEncanto()).ifPresent(e -> sb.append(' ').append(e.emoji()));
        }
        return sb.toString();
    }

    /** Nombre localizado del ítem equipado (con emoji), o «—» si la ranura está vacía. */
    private static String nombreEquipo(Locale locale, String itemId) {
        if (itemId == null) {
            return Messages.get(locale, "perfil.sinequipo");
        }
        return Items.porId(itemId)
                .map(i -> i.emoji() + " " + Messages.get(locale, "item." + i.id()))
                .orElse(itemId);
    }
}
