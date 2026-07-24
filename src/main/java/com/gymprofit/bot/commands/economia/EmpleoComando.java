package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Ascensos;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaService.ResultadoContratar;
import com.gymprofit.bot.util.Embeds;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /empleo} — bolsa de empleo (Fase 5c). Es la cara pública del opt-in de contratación: un jugador
 * ve qué empresas <b>de su rama</b> están contratando ({@code ver}) y puede solicitar entrar pulsando un
 * botón; el dueño o un directivo abre o cierra su empresa a la bolsa ({@code contratar}). La rama del
 * invocador la resuelve {@link EmpresaService#ramaDeJugador} (misma regla que decide dónde funda o
 * ingresa); las solicitudes reales las crea {@link EmpresaService#solicitarPorId} desde el
 * {@link com.gymprofit.bot.events.EmpleoListener listener} de la Task 3, no este comando.
 *
 * <p>El comando recibe también el {@link EmpresaRepositorio} porque el tablón son consultas de apoyo
 * ({@code contratandoDeRama}, {@code miembros}, {@code deMiembro}), no reglas de negocio: el service
 * expone las operaciones, el repo las lecturas para pintar la lista.
 */
public final class EmpleoComando implements Comando {

    private static final String NOMBRE = "empleo";
    /** Tope de empresas listadas: Discord admite 5 filas de 5 botones = 25 botones por mensaje. */
    private static final int MAX_EMPRESAS = 25;
    /** Botones por fila que admite Discord. */
    private static final int BOTONES_POR_FILA = 5;
    /**
     * Prefijo del customId del botón de solicitar entrada: {@code empleo:solicitar:<empresaId>}. Lo
     * consume el {@link com.gymprofit.bot.events.EmpleoListener listener} de la Task 3.
     */
    public static final String BOTON_SOLICITAR = "empleo:solicitar";

    private final EmpresaService empresa;
    private final EmpresaRepositorio repo;

    public EmpleoComando(EmpresaService empresa, EmpresaRepositorio repo) {
        this.empresa = empresa;
        this.repo = repo;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.empleo.desc"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.empleo.desc"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empleo.desc"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.empleo.ver.desc"),
                        sub("contratar", "comando.empleo.contratar.desc"));
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
            case "contratar" -> contratar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Abre o cierra tu empresa a la bolsa de empleo (solo dueño/directivo). Es gestión personal, así que
     * va <b>efímero</b>: el resultado (aparece / ya no aparece / no procede) solo lo ve quien lo pulsa.
     */
    private void contratar(SlashCommandInteractionEvent evento, Locale locale) {
        ResultadoContratar r = empresa.alternarContratando(evento.getUser().getIdLong());
        String mensaje = switch (r) {
            case ABIERTA -> Messages.get(locale, "empleo.contratar.abierta");
            case CERRADA -> Messages.get(locale, "empleo.contratar.cerrada");
            case SIN_EMPRESA -> Messages.get(locale, "empleo.contratar.sin_empresa");
            case NO_AUTORIZADO -> Messages.get(locale, "empleo.contratar.no_autorizado");
        };
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }

    /**
     * Tablón de empleo: las empresas de tu rama que están contratando. Sin trabajo no hay rama y no hay
     * nada que mostrar. Si ya perteneces a una empresa se listan sin botones (no puedes optar a otra); si
     * no, cada fila lleva su botón <i>Solicitar</i>. Público (la economía se juega a la vista). Se acota a
     * {@value #MAX_EMPRESAS} empresas (avisando del recorte) y se trocea la descripción si supera el tope.
     */
    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(false).queue();
        Optional<Ascensos.Rama> ramaOpt = empresa.ramaDeJugador(userId);
        if (ramaOpt.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empleo.sin_trabajo"))).setEphemeral(true).queue();
            return;
        }
        Ascensos.Rama rama = ramaOpt.get();
        String titulo = Messages.get(locale, "empleo.tablon.titulo",
                Messages.get(locale, "rama." + rama.name().toLowerCase(Locale.ROOT)));
        List<Empresa> contratando = repo.contratandoDeRama(rama.name());
        if (contratando.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                    titulo, Messages.get(locale, "empleo.tablon.vacio")).build()).queue();
            return;
        }
        // Si ya estás en una empresa se muestra el tablón informativo pero SIN botones: no puedes optar.
        boolean yaEnEmpresa = repo.deMiembro(userId).isPresent();
        boolean recortado = contratando.size() > MAX_EMPRESAS;
        List<Empresa> mostradas = recortado ? contratando.subList(0, MAX_EMPRESAS) : contratando;

        List<String> lineas = new ArrayList<>();
        for (Empresa e : mostradas) {
            lineas.add(Messages.get(locale, "empleo.tablon.fila",
                    e.nombre(), e.nivel(), repo.miembros(e.id()).size(), e.bote()));
        }
        List<String> bloques = new ArrayList<>(Embeds.partirEnBloques(lineas, Embeds.MAX_DESC));
        // Las notas (recorte / ya en empresa) se anexan al último bloque, donde también van los botones.
        StringBuilder ultimo = new StringBuilder(bloques.get(bloques.size() - 1));
        if (recortado) {
            ultimo.append("\n\n").append(Messages.get(locale, "empleo.tablon.recorte", MAX_EMPRESAS));
        }
        if (yaEnEmpresa) {
            ultimo.append("\n\n").append(Messages.get(locale, "empleo.ya_en_empresa"));
        }
        bloques.set(bloques.size() - 1, ultimo.toString());

        List<ActionRow> filas = yaEnEmpresa ? List.of() : botonesSolicitar(locale, mostradas);
        // Un embed por bloque; el último carga los botones (todos por debajo de la lista completa).
        for (int i = 0; i < bloques.size(); i++) {
            boolean esUltimo = i == bloques.size() - 1;
            WebhookMessageCreateAction<?> accion = evento.getHook().sendMessageEmbeds(
                    EmbedFactory.base(EmbedFactory.Tipo.STATS, locale, titulo, bloques.get(i)).build());
            if (esUltimo && !filas.isEmpty()) {
                accion = accion.setComponents(filas);
            }
            accion.queue();
        }
    }

    /**
     * Reparte un botón <i>Solicitar</i> por empresa en filas de {@value #BOTONES_POR_FILA} (tope de
     * Discord). El customId codifica el id de la empresa ({@code empleo:solicitar:<empresaId>}) para que
     * el listener sepa a cuál se solicita entrar.
     */
    private static List<ActionRow> botonesSolicitar(Locale locale, List<Empresa> empresas) {
        List<Button> botones = new ArrayList<>();
        for (Empresa e : empresas) {
            botones.add(Button.primary(BOTON_SOLICITAR + ":" + e.id(),
                    Messages.get(locale, "empleo.solicitar.boton")));
        }
        List<ActionRow> filas = new ArrayList<>();
        for (int i = 0; i < botones.size(); i += BOTONES_POR_FILA) {
            filas.add(ActionRow.of(botones.subList(i, Math.min(i + BOTONES_POR_FILA, botones.size()))));
        }
        return filas;
    }
}
