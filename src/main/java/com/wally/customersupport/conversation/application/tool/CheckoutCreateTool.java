package com.wally.customersupport.conversation.application.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.domain.model.CartStatus;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Idempotent checkout boundary backed by the existing cart application service. */
@Component
@Slf4j
public final class CheckoutCreateTool implements WcsTool<CheckoutCreateTool.Input, CheckoutCreateTool.Result> {

    public static final String NAME = WcsToolContractCatalog.CHECKOUT_CREATE;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final CartConversationHandler cartHandler;
    private final CartRepository cartRepository;
    private final ActorKeyGenerator actorKeyGenerator;

    public CheckoutCreateTool(
            CartConversationHandler cartHandler,
            CartRepository cartRepository,
            ActorKeyGenerator actorKeyGenerator) {
        this.cartHandler = Objects.requireNonNull(cartHandler, "cartHandler");
        this.cartRepository = Objects.requireNonNull(cartRepository, "cartRepository");
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
        var cart = cartRepository.findByConversationId(input.context().conversationId());
        if (cart.isEmpty() || !owns(input.context(), cart.get())
                || cart.get().getVersion() != input.cartVersion()
                || cart.get().getStatus() == CartStatus.CANCELLED) {
            return rejected("El carrito cambió o ya no está disponible.");
        }
        if (cart.get().getStatus() == CartStatus.CHECKOUT_PENDING) {
            return reused("Tu carrito ya tiene un link de pago activo.");
        }
        String response = cartHandler.handle(input.context(), new CartCommandParser.Command(
                        CartCommandParser.Action.CONFIRM, null, 1))
                .map(CartConversationHandler.Response::text)
                .orElse("No pude generar el link de pago.");
        CartJpaEntity refreshed = cartRepository.findByConversationId(input.context().conversationId()).orElse(null);
        boolean created = refreshed != null && refreshed.getStatus() == CartStatus.CHECKOUT_PENDING;
        Result result = created
                ? new Result(Status.CREATED, true, response.contains("http"), response)
                : rejected(response);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool", NAME);
        fields.put("status", result.status().name());
        fields.put("orderCreated", result.orderCreated());
        fields.put("paymentLinkCreated", result.paymentLinkCreated());
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", fields);
        return result;
    }

    private static Result rejected(String response) {
        return new Result(Status.REJECTED, false, false,
                response == null || response.isBlank() ? "No pude generar el link de pago." : response);
    }

    private static Result reused(String response) {
        return new Result(Status.REUSED, false, true, response);
    }

    private boolean owns(ConversationContext context, CartJpaEntity cart) {
        String actorKey = actorKeyGenerator.generate(context.channel(), context.externalCustomerId())
                .orElse(context.conversationId().toString());
        return actorKey.equals(cart.getActorKey()) && context.channel() == cart.getChannel();
    }

    public record Input(ConversationContext context, boolean confirmed, long cartVersion) {

        public Input {
            context = Objects.requireNonNull(context, "context");
            if (context.conversationId() == null) {
                throw new IllegalArgumentException("context.conversationId must not be null");
            }
            if (!confirmed) {
                throw new IllegalArgumentException("checkout requires explicit confirmation");
            }
            if (cartVersion < 1) {
                throw new IllegalArgumentException("cartVersion must be positive");
            }
        }
    }

    public enum Status {
        CREATED,
        REUSED,
        REJECTED
    }

    public record Result(Status status, boolean orderCreated, boolean paymentLinkCreated, String response) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            response = Objects.requireNonNull(response, "response");
        }
    }
}
