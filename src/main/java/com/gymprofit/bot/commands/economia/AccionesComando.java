package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaAccionRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.AccionEmpresasService;
import com.gymprofit.bot.services.AccionEmpresasService.PosicionVista;
import com.gymprofit.bot.services.AccionEmpresasService.ResultadoCompra;
import com.gymprofit.bot.services.AccionEmpresasService.ResultadoVenta;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /acciones} con subcomandos (comprar, vender, ver, cartera): la cara del mercado de
 * participaciones de empresa (F5). Compras participaciones del pool fijo de una empresa (tu dinero entra
 * al bote) y las vendes de vuelta (la empresa te paga del bote); ambas cotizan al precio actual, derivado
 * del prestigio. Toda la regla de dinero vive en {@link AccionEmpresasService}; aquí solo se traducen los
 * enums de resultado a embeds i18n.
 *
 * <p>Visibilidad (convención del proyecto, economía a la vista): los éxitos van <b>públicos</b> (se
 * celebra la operación) y {@code ver}/{@code cartera} son consultas públicas; los errores van
 * <b>efímeros</b> con el dato exacto. La empresa de {@code comprar}/{@code vender}/{@code ver} se elige
 * por autocompletado, cuyo valor es el id de la empresa (nombre visible → id como valor).
 */
public final class AccionesComando implements ComandoAutocompletable {

    private static final String NOMBRE = "acciones";
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;
    /** Tope de posiciones que se pintan en la cartera, para no desbordar el límite de un embed. */
    private static final int MAX_POSICIONES = 25;

    private final AccionEmpresasService service;
    private final EmpresaRepositorio repo;
    private final EmpresaAccionRepositorio accRepo;

    public AccionesComando(AccionEmpresasService service, EmpresaRepositorio repo,
                           EmpresaAccionRepositorio accRepo) {
        this.service = service;
        this.repo = repo;
        this.accRepo = accRepo;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.acciones.desc"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.acciones.desc"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.acciones.desc"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("comprar", "comando.acciones.comprar.desc").addOptions(empresa(), cantidad()),
                        sub("vender", "comando.acciones.vender.desc").addOptions(empresa(), cantidad()),
                        sub("ver", "comando.acciones.ver.desc").addOptions(empresa()),
                        sub("cartera", "comando.acciones.cartera.desc"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    /** Opción de empresa: STRING obligatoria y autocompletada (el valor de cada sugerencia es su id). */
    private static OptionData empresa() {
        return new OptionData(OptionType.STRING, "empresa",
                Messages.get(Messages.ES, "comando.acciones.opcion.empresa"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.acciones.opcion.empresa"));
    }

    /** Opción de cantidad: entero obligatorio, mínimo 1. */
    private static OptionData cantidad() {
        return new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.acciones.opcion.cantidad"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.acciones.opcion.cantidad"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "cartera" : evento.getSubcommandName();
        switch (sub) {
            case "comprar" -> comprar(evento, locale);
            case "vender" -> vender(evento, locale);
            case "ver" -> ver(evento, locale);
            case "cartera" -> cartera(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Compra participaciones de una empresa: tu dinero entra al bote. El éxito es un embed <b>público</b>
     * de celebración con la mención, la empresa, el coste y el precio unitario; cualquier fallo va
     * <b>efímero</b> con el dato exacto.
     */
    private void comprar(SlashCommandInteractionEvent evento, Locale locale) {
        OptionalLong idOpt = empresaIdDe(evento);
        if (idOpt.isEmpty()) {
            errorEfimero(evento, locale, "acciones.error.no_existe");
            return;
        }
        long empresaId = idOpt.getAsLong();
        int cantidad = evento.getOption("cantidad").getAsInt();
        ResultadoCompra r = service.comprar(evento.getUser().getIdLong(), empresaId, cantidad);
        if (r.estado() == AccionEmpresasService.EstadoCompra.OK) {
            String nombre = repo.porId(empresaId).map(Empresa::nombre).orElse("?");
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "acciones.comprar.ok", evento.getUser().getAsMention(),
                            r.cantidad(), nombre, r.coste(), r.precio()))).queue();
            return;
        }
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                mensajeCompraFallida(locale, r.estado()))).setEphemeral(true).queue();
    }

    /** Traduce un fallo de compra a su mensaje i18n (el éxito se trata aparte). */
    private static String mensajeCompraFallida(Locale locale, AccionEmpresasService.EstadoCompra estado) {
        return switch (estado) {
            case NO_EXISTE -> Messages.get(locale, "acciones.error.no_existe");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "acciones.error.cantidad");
            case SIN_PARTICIPACIONES_LIBRES -> Messages.get(locale, "acciones.error.sin_libres");
            case SIN_SALDO -> Messages.get(locale, "acciones.error.sin_saldo");
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
    }

    /**
     * Vende participaciones de vuelta a la empresa: te paga del bote al precio actual. El éxito es un embed
     * <b>público</b> con la mención, la empresa, el valor cobrado y el precio unitario; cualquier fallo va
     * <b>efímero</b>.
     */
    private void vender(SlashCommandInteractionEvent evento, Locale locale) {
        OptionalLong idOpt = empresaIdDe(evento);
        if (idOpt.isEmpty()) {
            errorEfimero(evento, locale, "acciones.error.no_existe");
            return;
        }
        long empresaId = idOpt.getAsLong();
        int cantidad = evento.getOption("cantidad").getAsInt();
        ResultadoVenta r = service.vender(evento.getUser().getIdLong(), empresaId, cantidad);
        if (r.estado() == AccionEmpresasService.EstadoVenta.OK) {
            String nombre = repo.porId(empresaId).map(Empresa::nombre).orElse("?");
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "acciones.vender.ok", evento.getUser().getAsMention(),
                            r.cantidad(), nombre, r.valor(), r.precio()))).queue();
            return;
        }
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                mensajeVentaFallida(locale, r.estado()))).setEphemeral(true).queue();
    }

    /** Traduce un fallo de venta a su mensaje i18n (el éxito se trata aparte). */
    private static String mensajeVentaFallida(Locale locale, AccionEmpresasService.EstadoVenta estado) {
        return switch (estado) {
            case NO_EXISTE -> Messages.get(locale, "acciones.error.no_existe");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "acciones.error.cantidad");
            case SIN_PARTICIPACIONES -> Messages.get(locale, "acciones.error.sin_participaciones");
            case EMPRESA_SIN_FONDOS -> Messages.get(locale, "acciones.error.empresa_sin_fondos");
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
    }

    /**
     * Muestra la ficha de participaciones de una empresa: cuántas se han colocado del pool, el precio
     * actual y cuántas tienes tú (con su valor). Pública (la economía se juega a la vista). Si la empresa
     * no existe, error <b>efímero</b>.
     */
    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        OptionalLong idOpt = empresaIdDe(evento);
        if (idOpt.isEmpty()) {
            errorEfimero(evento, locale, "acciones.error.no_existe");
            return;
        }
        long empresaId = idOpt.getAsLong();
        Optional<Empresa> empOpt = repo.porId(empresaId);
        if (empOpt.isEmpty()) {
            errorEfimero(evento, locale, "acciones.error.no_existe");
            return;
        }
        Empresa emp = empOpt.get();
        long precio = service.precioActual(emp);
        int tuyas = accRepo.participacionesDe(empresaId, evento.getUser().getIdLong());
        String cuerpo = Messages.get(locale, "acciones.ver.cuerpo",
                accRepo.vendidasDe(empresaId), precio, tuyas, tuyas * precio);
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "acciones.ver.titulo", emp.nombre()), cuerpo).build()).queue();
    }

    /**
     * Pinta tu cartera de participaciones en todas las empresas, valorada a precio actual, con el valor
     * total al pie. Pública. Si no tienes ninguna, avisa de que está vacía.
     */
    private void cartera(SlashCommandInteractionEvent evento, Locale locale) {
        List<PosicionVista> posiciones = service.cartera(evento.getUser().getIdLong());
        String cuerpo;
        if (posiciones.isEmpty()) {
            cuerpo = Messages.get(locale, "acciones.cartera.vacia");
        } else {
            // Se acota a MAX_POSICIONES para no desbordar el límite de descripción de un embed; el valor
            // total es el de las posiciones mostradas y, si se recorta, se avisa (mismo criterio que /empleo).
            boolean recortado = posiciones.size() > MAX_POSICIONES;
            List<PosicionVista> mostradas = recortado
                    ? posiciones.subList(0, MAX_POSICIONES) : posiciones;
            StringBuilder sb = new StringBuilder();
            long total = 0;
            for (PosicionVista p : mostradas) {
                sb.append(Messages.get(locale, "acciones.cartera.linea",
                        p.empresa(), p.cantidad(), p.valor())).append('\n');
                total += p.valor();
            }
            sb.append(Messages.get(locale, "acciones.cartera.total", total));
            if (recortado) {
                sb.append('\n').append(Messages.get(locale, "acciones.cartera.recorte", MAX_POSICIONES));
            }
            cuerpo = sb.toString();
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "acciones.cartera.titulo"), cuerpo).build()).queue();
    }

    /**
     * Resuelve el id de la empresa de la opción {@code empresa}. El valor viene del autocompletado (el id),
     * pero un usuario puede teclear texto libre y enviarlo sin elegir sugerencia: por eso se parsea con
     * guarda y se devuelve {@link OptionalLong#empty()} si no es un id válido (mismo patrón que los ids
     * tecleados de {@code /unban}).
     */
    private static OptionalLong empresaIdDe(SlashCommandInteractionEvent evento) {
        try {
            return OptionalLong.of(Long.parseLong(evento.getOption("empresa").getAsString().trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Responde un error <b>efímero</b> con el mensaje i18n dado (convención del proyecto para fallos). */
    private static void errorEfimero(SlashCommandInteractionEvent evento, Locale locale, String clave) {
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, clave))).setEphemeral(true).queue();
    }

    /**
     * Autocompleta la empresa con el catálogo entero (nombre visible → id como valor), filtrado por lo
     * tecleado (contiene, sin distinguir mayúsculas). Responde siempre (aunque vacío) y sin {@code defer}:
     * Discord da 3 s. Mismo patrón que el resto de autocompletados del proyecto.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        if (!"empresa".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        String tecleado = evento.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> opciones = repo.todas().stream()
                .filter(e -> e.nombre().toLowerCase(Locale.ROOT).contains(tecleado))
                .limit(MAX_SUGERENCIAS)
                .map(e -> new Command.Choice(e.nombre(), String.valueOf(e.id())))
                .toList();
        evento.replyChoices(opciones).queue();
    }
}
