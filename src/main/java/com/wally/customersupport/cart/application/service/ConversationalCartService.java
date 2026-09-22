package com.wally.customersupport.cart.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.cart.application.port.out.CartCheckoutCreator;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.domain.model.CartStatus;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartItemJpaEntity;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogResponseFormatter;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalCartService implements CartConversationHandler {

    private static final String EMPTY_CART = "Tu carrito está vacío. Podés decirme qué producto querés agregar.";
    private static final String CHECKOUT_PENDING = "Tu carrito ya tiene un link de pago activo. "
            + "Si querés modificarlo, decime cancelar compra y luego actualizamos el carrito.";
    private static final String CHECKOUT_UNAVAILABLE = "No pude generar el link de pago en este momento. "
            + "Tu carrito sigue guardado y no se creó un nuevo pedido.";
    private static final String VARIANT_REQUIRED = "Para agregar un producto necesito una única variante. "
            + "Indicame el producto, talle y color.";
    private static final String CART_REQUIRED = "No pude identificar el producto para el carrito. "
            + "Indicame el nombre, talle y color.";

    private final CartRepository cartRepository;
    private final CartCatalogReader catalogReader;
    private final CatalogConversationService catalogConversationService;
    private final CartCheckoutCreator checkoutCreator;
    private final ActorKeyGenerator actorKeyGenerator;
    private final Clock clock;

    @Override
    public boolean recognizes(String message) {
        return CartCommandParser.isRecognized(message);
    }

    @Override
    @Transactional
    public Optional<Response> handle(ConversationContext context) {
        return handle(context, CartCommandParser.parse(context == null ? null : context.latestMessage()));
    }

    @Override
    @Transactional
    public Optional<Response> handle(ConversationContext context, CartCommandParser.Command command) {
        if (command.action() == CartCommandParser.Action.NONE) {
            return Optional.empty();
        }
        if (context == null || context.conversationId() == null) {
            return Optional.of(new Response(CART_REQUIRED));
        }

        return switch (command.action()) {
            case ADD -> Optional.of(new Response(add(context, command)));
            case VIEW -> Optional.of(new Response(view(context)));
            case REMOVE -> Optional.of(new Response(remove(context, command)));
            case CLEAR -> Optional.of(new Response(clear(context)));
            case REVIEW_CHECKOUT -> Optional.of(new Response(reviewCheckout(context)));
            case CONFIRM -> Optional.of(new Response(confirm(context)));
            case CANCEL_CHECKOUT -> Optional.of(new Response(cancelCheckout(context)));
            case DEFER -> defer(context);
            case NONE -> Optional.empty();
        };
    }

    private String add(ConversationContext context, CartCommandParser.Command command) {
        Optional<CatalogFact> fact = resolveSingleFact(context, command, null);
        if (fact.isEmpty()) {
            return resolveCatalogMessage(context, command);
        }
        if (!fact.get().available()) {
            logOutcome(context, "ADD_INSUFFICIENT_STOCK");
            return "Esa variante no tiene stock disponible en este momento.";
        }

        Instant now = clock.instant();
        CartJpaEntity cart = findOrCreate(context, fact.get(), now);
        if (cart == null) {
            return CART_REQUIRED;
        }
        if (!cart.getCurrency().equalsIgnoreCase(fact.get().currency())) {
            logOutcome(context, "CURRENCY_MISMATCH");
            return "No puedo combinar productos de monedas distintas en el mismo carrito.";
        }
        if (cart.getStatus() != CartStatus.ACTIVE) {
            logOutcome(context, "ADD_REJECTED_CHECKOUT_PENDING");
            return CHECKOUT_PENDING;
        }
        int currentQuantity = cart.getItems().stream()
                .filter(item -> item.getSku().equalsIgnoreCase(fact.get().sku()))
                .mapToInt(CartItemJpaEntity::getQuantity)
                .findFirst()
                .orElse(0);
        if (currentQuantity + command.quantity() > fact.get().stock()) {
            logOutcome(context, "ADD_INSUFFICIENT_STOCK");
            return String.format(
                    Locale.ROOT,
                    "No puedo agregar %d unidades. El stock actual de %s es %d.",
                    command.quantity(), fact.get().productName(), fact.get().stock());
        }
        try {
            cart.addOrIncrement(fact.get().sku(), command.quantity(), now);
            cartRepository.saveAndFlush(cart);
        } catch (CartJpaEntity.CartStateException exception) {
            logOutcome(context, exception.getMessage());
            return CHECKOUT_PENDING;
        }
        logOutcome(context, "ADDED");
        return "Agregué " + command.quantity() + " x " + fact.get().productName() + " ("
                + fact.get().sku() + ") al carrito.\n\n" + formatSummary(cart);
    }

    private String view(ConversationContext context) {
        return findOwned(context)
                .map(this::formatSummary)
                .orElse(EMPTY_CART);
    }

    private String remove(ConversationContext context, CartCommandParser.Command command) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getItems().isEmpty()) {
            return EMPTY_CART;
        }
        CartJpaEntity cart = stored.get();
        Optional<CatalogFact> fact = resolveSingleFact(context, command, cart);
        if (fact.isEmpty()) {
            return command.query() == null || command.query().isEmpty()
                    ? "Indicame qué producto querés sacar del carrito."
                    : VARIANT_REQUIRED;
        }
        try {
            cart.remove(fact.get().sku(), command.quantity(), clock.instant());
            cartRepository.saveAndFlush(cart);
        } catch (CartJpaEntity.CartStateException exception) {
            logOutcome(context, exception.getMessage());
            return "CART_CHECKOUT_PENDING".equals(exception.getMessage())
                    ? CHECKOUT_PENDING
                    : "Ese producto no está en el carrito.";
        }
        logOutcome(context, "REMOVED");
        return "Actualicé tu carrito.\n\n" + formatSummary(cart);
    }

    private String clear(ConversationContext context) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getItems().isEmpty()) {
            return EMPTY_CART;
        }
        CartJpaEntity cart = stored.get();
        try {
            cart.clear(clock.instant());
            cartRepository.saveAndFlush(cart);
        } catch (CartJpaEntity.CartStateException exception) {
            return CHECKOUT_PENDING;
        }
        logOutcome(context, "CLEARED");
        return "Listo, vacié tu carrito. Podés armar uno nuevo cuando quieras.";
    }

    private String confirm(ConversationContext context) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getItems().isEmpty()) {
            return EMPTY_CART;
        }
        CartJpaEntity cart = stored.get();
        if (cart.getStatus() != CartStatus.ACTIVE) {
            logOutcome(context, "CHECKOUT_ALREADY_PENDING");
            return CHECKOUT_PENDING;
        }
        List<CartCheckoutCreator.Item> items = cart.getItems().stream()
                .map(item -> new CartCheckoutCreator.Item(item.getSku(), item.getQuantity()))
                .toList();
        Optional<CartCheckoutCreator.CartCheckout> checkout = checkoutCreator.create(
                new CartCheckoutCreator.CreateCartCheckoutRequest(
                        context.conversationId(), customerReference(context), cart.getId(), cart.getVersion(), items));
        if (checkout.isEmpty()) {
            logOutcome(context, "CHECKOUT_UNAVAILABLE");
            return CHECKOUT_UNAVAILABLE;
        }
        cart.markCheckoutPending(checkout.get().orderId(), clock.instant());
        cartRepository.saveAndFlush(cart);
        logOutcome(context, "CHECKOUT_CREATED");
        return String.format(
                Locale.ROOT,
                "Listo. Preparé tu pedido con el contenido de tu carrito por un total de %s %s.\n"
                        + "Podés completar el pago acá: %s",
                checkout.get().total(), checkout.get().currency(), checkout.get().checkoutUrl());
    }

    private String reviewCheckout(ConversationContext context) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getItems().isEmpty()) {
            return EMPTY_CART;
        }
        CartJpaEntity cart = stored.get();
        if (cart.getStatus() != CartStatus.ACTIVE) {
            return CHECKOUT_PENDING;
        }
        return formatSummary(cart,
                "¿Confirmás la compra? Respondé 'confirmar compra' para generar un único link de pago. "
                        + "Si querés cambiar algo, decime qué agregar o sacar.");
    }

    private String cancelCheckout(ConversationContext context) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getStatus() != CartStatus.CHECKOUT_PENDING) {
            return "No hay un checkout activo para cancelar. Tu carrito sigue disponible para modificar.";
        }
        CartJpaEntity cart = stored.get();
        checkoutCreator.cancelActive(cart.getId());
        cart.cancelCheckout(clock.instant());
        cartRepository.saveAndFlush(cart);
        logOutcome(context, "CHECKOUT_CANCELLED");
        return "Listo, cancelé el checkout anterior. Tu carrito sigue guardado y podés modificarlo antes de generar otro link.";
    }

    private Optional<Response> defer(ConversationContext context) {
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty() || stored.get().getStatus() != CartStatus.CHECKOUT_PENDING) {
            return Optional.empty();
        }
        CartJpaEntity cart = stored.get();
        checkoutCreator.cancelActive(cart.getId());
        cart.cancelCheckout(clock.instant());
        cartRepository.saveAndFlush(cart);
        logOutcome(context, "CHECKOUT_CANCELLED");
        return Optional.of(new Response(
                "Entendido, cancelé el checkout anterior. No genero ningún pedido nuevo. "
                        + "Tu carrito queda guardado para cuando quieras retomarlo."));
    }

    @Override
    @Transactional
    public void reset(ConversationContext context) {
        if (context == null || context.conversationId() == null) {
            return;
        }
        Optional<CartJpaEntity> stored = findOwned(context);
        if (stored.isEmpty()) {
            return;
        }
        CartJpaEntity cart = stored.get();
        if (cart.getStatus() == CartStatus.CHECKOUT_PENDING) {
            checkoutCreator.cancelActive(cart.getId());
        }
        cart.reset(clock.instant());
        cartRepository.saveAndFlush(cart);
        logOutcome(context, "RESET_WITH_CONVERSATION");
    }

    private Optional<CatalogFact> resolveSingleFact(
            ConversationContext context,
            CartCommandParser.Command command,
            CartJpaEntity cart) {
        Optional<CatalogQuery> query = resolveQuery(context, command);
        if (query.isEmpty() || query.get().isEmpty()) {
            if (cart != null && cart.getItems().size() == 1) {
                return catalogReader.findBySku(cart.getItems().getFirst().getSku()).map(ConversationalCartService::toFact);
            }
            return Optional.empty();
        }
        boolean exactLookup = command.query() != null && !command.query().isEmpty();
        Optional<CatalogSearchResult> result = exactLookup
                ? catalogConversationService.searchExact(query.get())
                : catalogConversationService.search(query.get(), context.recentMessages(), context.latestMessage());
        logCatalogLookup(context, command, query.get(), exactLookup, result);
        if (result.isEmpty()
                || result.get().status() != CatalogSearchResult.Status.MATCHED
                || result.get().facts().isEmpty()) {
            return Optional.empty();
        }
        List<CatalogFact> facts = result.get().facts();
        if (facts.size() == 1) {
            return Optional.of(facts.getFirst());
        }
        if (cart == null) {
            return Optional.empty();
        }
        List<CatalogFact> inCart = facts.stream()
                .filter(fact -> cart.getItems().stream().anyMatch(item -> item.getSku().equalsIgnoreCase(fact.sku())))
                .toList();
        return inCart.size() == 1 ? Optional.of(inCart.getFirst()) : Optional.empty();
    }

    private Optional<CatalogQuery> resolveQuery(ConversationContext context, CartCommandParser.Command command) {
        if (command.query() != null && !command.query().isEmpty()) {
            return Optional.of(command.query());
        }
        if (context.selection() != null
                && context.selection().workingMemory() != null
                && context.selection().workingMemory().focusedSku() != null) {
            return Optional.of(new CatalogQuery(
                    null,
                    context.selection().workingMemory().focusedSku(),
                    null,
                    null));
        }
        for (String previous : context.recentMessages()) {
            Optional<CatalogQuery> candidate = CatalogQueryParser.parseConversation(
                    context.recentMessages(), previous).filter(query -> !query.isEmpty());
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private CartJpaEntity findOrCreate(ConversationContext context, CatalogFact fact, Instant now) {
        String actorKey = actorKey(context);
        Optional<CartJpaEntity> existing = cartRepository.findByConversationId(context.conversationId());
        if (existing.isPresent()) {
            CartJpaEntity cart = existing.get();
            if (!actorKey.equals(cart.getActorKey()) || cart.getChannel() != context.channel()) {
                logOutcome(context, "OWNERSHIP_REJECTED");
                return null;
            }
            return cart;
        }
        if (context.channel() == null) {
            return null;
        }
        return new CartJpaEntity(
                java.util.UUID.randomUUID(), context.conversationId(), actorKey, context.channel(), fact.currency(), now);
    }

    private Optional<CartJpaEntity> findOwned(ConversationContext context) {
        String actorKey = actorKey(context);
        return cartRepository.findByConversationId(context.conversationId())
                .filter(cart -> actorKey.equals(cart.getActorKey()) && cart.getChannel() == context.channel());
    }

    private String actorKey(ConversationContext context) {
        return actorKeyGenerator.generate(context.channel(), context.externalCustomerId())
                .orElse(context.conversationId().toString());
    }

    private String resolveCatalogMessage(ConversationContext context, CartCommandParser.Command command) {
        Optional<CatalogQuery> query = resolveQuery(context, command);
        if (query.isEmpty()) {
            return CART_REQUIRED;
        }
        return catalogConversationService.search(query.get(), context.recentMessages(), context.latestMessage())
                .map(CatalogResponseFormatter::render)
                .orElse(VARIANT_REQUIRED);
    }

    private void logCatalogLookup(
            ConversationContext context,
            CartCommandParser.Command command,
            CatalogQuery query,
            boolean exactLookup,
            Optional<CatalogSearchResult> result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", "conversation.cart.catalog_lookup");
        fields.put("lookupMode", exactLookup ? "EXACT" : "CONTEXTUAL");
        fields.put("action", command.action().name());
        fields.put("quantity", command.quantity());
        fields.put("queryFilterCount", query.presentFieldCount());
        fields.put("queryFilters", query.presentFieldNames());
        fields.put("queryName", query.name());
        fields.put("querySize", query.size());
        fields.put("queryColor", query.color());
        fields.put("queryProductType", query.productType());
        fields.put("querySku", query.sku());
        fields.put("resultStatus", result.map(value -> value.status().name()).orElse("EMPTY"));
        fields.put("resultCount", result.map(value -> value.facts().size()).orElse(0));
        if (context != null) {
            if (context.conversationId() != null) {
                fields.put("correlationId", context.conversationId());
            }
            if (context.channel() != null) {
                fields.put("channel", context.channel().name());
            }
        }
        StructuredEventLog.info(log, "CART_CATALOG_LOOKUP", fields);
    }

    private String formatSummary(CartJpaEntity cart) {
        return formatSummary(cart,
                "Si querés seguir modificándolo, decime qué agregar o sacar. Cuando esté listo, "
                        + "escribí: confirmar compra.");
    }

    private String formatSummary(CartJpaEntity cart, String closingInstruction) {
        if (cart.getItems().isEmpty()) {
            return EMPTY_CART;
        }
        List<SummaryLine> lines = new ArrayList<>();
        for (CartItemJpaEntity item : cart.getItems()) {
            catalogReader.findBySku(item.getSku()).ifPresentOrElse(
                    catalog -> lines.add(new SummaryLine(catalog.productName(), catalog.sku(), catalog.size(),
                            catalog.color(), catalog.unitPrice(), catalog.currency(), item.getQuantity(),
                            catalog.stock(), catalog.active())),
                    () -> lines.add(new SummaryLine("Producto no disponible", item.getSku(), "", "",
                            BigDecimal.ZERO, cart.getCurrency(), item.getQuantity(), 0, false)));
        }
        BigDecimal total = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder response = new StringBuilder("Tu carrito:\n");
        for (SummaryLine line : lines) {
            response.append("- ").append(line.quantity()).append(" x ").append(line.productName()).append(" — ")
                    .append(line.color()).append(", talle ").append(line.size()).append(" — ")
                    .append(money(line.unitPrice())).append(" ").append(line.currency()).append(" c/u — subtotal: ")
                    .append(money(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))).append(" — ")
                    .append(line.active() && line.stock() >= line.quantity()
                            ? "stock disponible: " + line.stock() : "sin stock suficiente")
                    .append(" (SKU: ").append(line.sku()).append(")\n");
        }
        response.append("Total: ").append(money(total)).append(" ").append(cart.getCurrency()).append("\n")
                .append(closingInstruction);
        return response.toString();
    }

    private static CatalogFact toFact(CartCatalogReader.CatalogItem item) {
        return new CatalogFact(item.productName(), item.sku(), item.size(), item.color(), item.unitPrice(),
                item.currency(), item.stock());
    }

    private void logOutcome(ConversationContext context, String result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", "conversation.cart");
        fields.put("result", result);
        if (context != null && context.conversationId() != null) {
            fields.put("correlationId", context.conversationId());
        }
        if (context != null && context.channel() != null) {
            fields.put("channel", context.channel().name());
        }
        StructuredEventLog.info(log, "CONVERSATIONAL_CART", fields);
    }

    private String customerReference(ConversationContext context) {
        String channel = context.channel() == null ? "unknown" : context.channel().name().toLowerCase(Locale.ROOT);
        // Orders must not persist a raw phone number or external chat id. The
        // actor key is stable for ownership and deliberately non-reversible.
        return channel + ":" + actorKey(context);
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record SummaryLine(
            String productName,
            String sku,
            String size,
            String color,
            BigDecimal unitPrice,
            String currency,
            int quantity,
            int stock,
            boolean active) {
    }
}
