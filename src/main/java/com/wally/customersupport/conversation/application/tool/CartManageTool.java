package com.wally.customersupport.conversation.application.tool;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Executes cart operations through the existing ownership-checked handler. */
@Component
@Slf4j
public final class CartManageTool implements WcsTool<CartManageTool.Input, CartManageTool.Result> {

    public static final String NAME = WcsToolContractCatalog.CART_MANAGE;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final CartConversationHandler handler;
    private final CartRepository cartRepository;
    private final CartCatalogReader catalogReader;
    private final ActorKeyGenerator actorKeyGenerator;

    public CartManageTool(
            CartConversationHandler handler,
            CartRepository cartRepository,
            CartCatalogReader catalogReader,
            ActorKeyGenerator actorKeyGenerator) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.cartRepository = Objects.requireNonNull(cartRepository, "cartRepository");
        this.catalogReader = Objects.requireNonNull(catalogReader, "catalogReader");
        this.actorKeyGenerator = Objects.requireNonNull(actorKeyGenerator, "actorKeyGenerator");
    }

    @Override
    public WcsToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Result execute(Input input) {
        Objects.requireNonNull(input, "input");
        CartCommandParser.Command command = new CartCommandParser.Command(
                input.operation().parserAction(), input.query(), input.quantity());
        String response = handler.handle(input.context(), command)
                .map(CartConversationHandler.Response::text)
                .orElse("No pude ejecutar la operación del carrito.");
        CartSnapshot snapshot = snapshot(input.context());
        Result result = new Result(
                status(input.operation(), response),
                snapshot.itemCount(),
                snapshot.total(),
                input.operation() == Operation.REVIEW,
                response,
                snapshot.currency());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool", NAME);
        fields.put("operation", input.operation().name());
        fields.put("status", result.status().name());
        fields.put("itemCount", result.itemCount());
        fields.put("requiresConfirmation", result.requiresConfirmation());
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", fields);
        return result;
    }

    private CartSnapshot snapshot(ConversationContext context) {
        if (context == null || context.conversationId() == null) {
            return new CartSnapshot(0, BigDecimal.ZERO, null);
        }
        return cartRepository.findByConversationId(context.conversationId())
                .filter(cart -> cart.getChannel() == context.channel())
                .filter(cart -> actorKey(context).equals(cart.getActorKey()))
                .map(this::snapshot)
                .orElse(new CartSnapshot(0, BigDecimal.ZERO, null));
    }

    private String actorKey(ConversationContext context) {
        return actorKeyGenerator.generate(context.channel(), context.externalCustomerId())
                .orElse(context.conversationId().toString());
    }

    private CartSnapshot snapshot(CartJpaEntity cart) {
        int itemCount = cart.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
        BigDecimal total = BigDecimal.ZERO;
        String currency = cart.getCurrency();
        for (var item : cart.getItems()) {
            var catalogItem = catalogReader.findBySku(item.getSku());
            if (catalogItem.isEmpty()) {
                return new CartSnapshot(itemCount, null, currency);
            }
            total = total.add(catalogItem.get().unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return new CartSnapshot(itemCount, total, currency);
    }

    private static Status status(Operation operation, String response) {
        if (response == null || response.isBlank() || response.startsWith("No pude")) {
            return Status.REJECTED;
        }
        return switch (operation) {
            case ADD, REMOVE -> Status.UPDATED;
            case VIEW, REVIEW -> Status.VIEWED;
            case CLEAR -> Status.CLEARED;
            case CONFIRM -> Status.CHECKOUT_CREATED;
            case CANCEL -> Status.CANCELLED;
            case DEFER -> Status.DEFERRED;
        };
    }

    public record Input(
            ConversationContext context,
            Operation operation,
            CatalogQuery query,
            int quantity) {

        public Input {
            context = Objects.requireNonNull(context, "context");
            operation = Objects.requireNonNull(operation, "operation");
            query = query == null ? CatalogQuery.empty() : query;
            if (quantity < 1 || quantity > 100) {
                throw new IllegalArgumentException("quantity must be between 1 and 100");
            }
        }
    }

    public enum Operation {
        ADD(CartCommandParser.Action.ADD),
        VIEW(CartCommandParser.Action.VIEW),
        REMOVE(CartCommandParser.Action.REMOVE),
        CLEAR(CartCommandParser.Action.CLEAR),
        REVIEW(CartCommandParser.Action.REVIEW_CHECKOUT),
        CONFIRM(CartCommandParser.Action.CONFIRM),
        CANCEL(CartCommandParser.Action.CANCEL_CHECKOUT),
        DEFER(CartCommandParser.Action.DEFER);

        private final CartCommandParser.Action parserAction;

        Operation(CartCommandParser.Action parserAction) {
            this.parserAction = parserAction;
        }

        CartCommandParser.Action parserAction() {
            return parserAction;
        }
    }

    public enum Status {
        UPDATED,
        VIEWED,
        CLEARED,
        CHECKOUT_CREATED,
        CANCELLED,
        DEFERRED,
        REJECTED
    }

    public record Result(
            Status status,
            int itemCount,
            BigDecimal total,
            boolean requiresConfirmation,
            String response,
            String currency) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            response = Objects.requireNonNull(response, "response");
            if (itemCount < 0) {
                throw new IllegalArgumentException("itemCount must not be negative");
            }
            if (total != null && total.signum() < 0) {
                throw new IllegalArgumentException("total must not be negative");
            }
        }
    }

    private record CartSnapshot(int itemCount, BigDecimal total, String currency) {
    }
}
