package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.ApuestaService.Color;
import com.gymprofit.bot.services.ApuestaService.Resultado;
import com.gymprofit.bot.services.DueloService;
import com.gymprofit.bot.services.DueloService.Duelo;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Locale;

/**
 * {@code /casino} con subcomandos (coinflip, dado, ruleta, duelo). Consolida los juegos de azar de
 * ficción en un solo comando para no gastar cupo de slash commands. Los tres juegos solitarios
 * comparten cooldown; el duelo lo confirma el retado con botones ({@code CombateListener}/
 * {@code DueloListener}). Todo es ficción.
 */
public final class CasinoComando implements Comando {

    private static final String NOMBRE = "casino";

    private final ApuestaService apuestas;
    private final DueloService duelos;
    private final Cooldown cooldown;

    public CasinoComando(ApuestaService apuestas, DueloService duelos, Cooldown cooldown) {
        this.apuestas = apuestas;
        this.duelos = duelos;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData cara = new OptionData(OptionType.STRING, "cara",
                Messages.get(Messages.ES, "comando.coinflip.opcion.cara"), true)
                .addChoice("cara", "cara").addChoice("cruz", "cruz");
        OptionData numero = new OptionData(OptionType.INTEGER, "numero",
                Messages.get(Messages.ES, "comando.dado.opcion.numero"), true).setMinValue(1).setMaxValue(6);
        OptionData color = new OptionData(OptionType.STRING, "color",
                Messages.get(Messages.ES, "comando.ruleta.opcion.color"), true)
                .addChoice("🔴 rojo", "ROJO").addChoice("⚫ negro", "NEGRO").addChoice("🟢 verde", "VERDE");
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.duelo.opcion.usuario"), true);
        OptionData apuestaDuelo = new OptionData(OptionType.INTEGER, "apuesta",
                Messages.get(Messages.ES, "comando.apuesta.opcion.apuesta"), true)
                .setMinValue(ApuestaService.APUESTA_MIN).setMaxValue(ApuestaService.APUESTA_MAX);
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.casino.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.casino.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.casino.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("coinflip", "comando.coinflip.descripcion")
                                .addOptions(GamblingHelper.opcionApuesta(), cara),
                        sub("dado", "comando.dado.descripcion")
                                .addOptions(numero, GamblingHelper.opcionApuesta()),
                        sub("ruleta", "comando.ruleta.descripcion")
                                .addOptions(GamblingHelper.opcionApuesta(), color),
                        sub("duelo", "comando.duelo.descripcion").addOptions(usuario, apuestaDuelo));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        switch (evento.getSubcommandName() == null ? "" : evento.getSubcommandName()) {
            case "coinflip" -> coinflip(evento);
            case "dado" -> dado(evento);
            case "ruleta" -> ruleta(evento);
            case "duelo" -> duelo(evento);
            default -> { }
        }
    }

    private void coinflip(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        long apuesta = evento.getOption("apuesta").getAsLong();
        boolean cara = evento.getOption("cara").getAsString().equals("cara");
        evento.deferReply(false).queue();
        Resultado r = apuestas.coinflip(evento.getUser().getIdLong(), apuesta, cara);
        String salio = Messages.get(locale, r.tirada() == 1 ? "coinflip.cara" : "coinflip.cruz");
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "coinflip.resultado", salio))).queue();
    }

    private void dado(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        int numero = evento.getOption("numero").getAsInt();
        long apuesta = evento.getOption("apuesta").getAsLong();
        evento.deferReply(false).queue();
        Resultado r = apuestas.dado(evento.getUser().getIdLong(), apuesta, numero);
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "dado.resultado", r.tirada(), numero))).queue();
    }

    private void ruleta(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        long apuesta = evento.getOption("apuesta").getAsLong();
        Color color = Color.valueOf(evento.getOption("color").getAsString());
        evento.deferReply(false).queue();
        Resultado r = apuestas.ruleta(evento.getUser().getIdLong(), apuesta, color);
        String salio = Messages.get(locale, "ruleta.color." + colorDe(r.tirada()));
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "ruleta.resultado", r.tirada(), salio))).queue();
    }

    private void duelo(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User retado = evento.getOption("usuario").getAsUser();
        long apuesta = evento.getOption("apuesta").getAsLong();
        long retador = evento.getUser().getIdLong();
        if (retado.isBot() || retado.getIdLong() == retador) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "duelo.destino"))).setEphemeral(true).queue();
            return;
        }
        long id = duelos.proponer(new Duelo(retador, retado.getIdLong(), apuesta));
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "duelo.titulo"),
                Messages.get(locale, "duelo.reto", "<@" + retador + ">", retado.getAsMention(), apuesta))
                .build();
        evento.replyEmbeds(embed).setComponents(ActionRow.of(
                        Button.success("duelo:acc:" + retado.getIdLong() + ":" + id,
                                Messages.get(locale, "duelo.aceptar")),
                        Button.danger("duelo:rej:" + retado.getIdLong() + ":" + id,
                                Messages.get(locale, "duelo.rechazar"))))
                .queue();
    }

    private static String colorDe(int slot) {
        return slot == 0 ? "verde" : (slot <= 18 ? "rojo" : "negro");
    }
}
