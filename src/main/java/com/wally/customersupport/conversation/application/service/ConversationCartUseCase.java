package com.wally.customersupport.conversation.application.service;

import java.util.Optional;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Owns deterministic and routed cart operations at the conversation boundary.
 *
 * <p>Explicit product, variant and quantity words remain deterministic. The
 * router may select the operation, but it cannot replace validated cart
 * selectors with free-form model output.</p>
 */
@Service
@Slf4j
public class ConversationCartUseCase {

    private static final String SAFE_FALLBACK = "No pude interpretar la consulta. "
            + "Podés preguntarme por productos, stock, horarios o políticas de la tienda.";

    private final CartConversationHandler cartConversationHandler;
    private final ConversationExecutionPlanFactory executionPlanFactory;
    private final ConversationWorkingMemoryReferenceResolver referenceResolver =
            new ConversationWorkingMemoryReferenceResolver();

    @Autowired
    public ConversationCartUseCase(
            CartConversationHandler cartConversationHandler,
            ConversationExecutionPlanFactory executionPlanFactory) {
        this.cartConversationHandler = cartConversationHandler;
        this.executionPlanFactory = executionPlanFactory;
    }

    public boolean isPurchaseDeferral(String message) {
        return CatalogQueryParser.isPurchaseDeferral(message);
    }

    public Optional<CartConversationHandler.Response> handleBeforeRouting(ConversationContext context) {
        boolean explicitCartCommand = cartConversationHandler.recognizes(context.latestMessage());
        boolean implicitCartAddition = isImplicitCartAddition(context);
        if (!explicitCartCommand && !implicitCartAddition
                && !isPurchaseDeferral(context.latestMessage())) {
            return Optional.empty();
        }
        try {
            // Keep explicit product, variant and quantity data deterministic
            // even when this fast path runs before model classification.
            CartCommandParser.Command command = CartCommandParser.parse(
                    context.latestMessage(), implicitCartAddition);
            if (command.action() == CartCommandParser.Action.ADD
                    || command.action() == CartCommandParser.Action.REMOVE) {
                var reference = referenceResolver.resolveForCartMutation(context);
                if (reference.isPresent()) {
                    command = new CartCommandParser.Command(command.action(), reference.get(), command.quantity());
                }
            }
            return cartConversationHandler.handle(
                    context,
                    command);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "CONVERSATIONAL_CART_FAILED", java.util.Map.of(
                    "errorType", exception.getClass().getSimpleName()));
            return Optional.of(new CartConversationHandler.Response(SAFE_FALLBACK));
        }
    }

    public Optional<CartConversationHandler.Response> handleRouted(
            ConversationContext context,
            ConversationIntentDecision decision) {
        if (decision == null
                || !decision.action().isCartOperation()
                || !executionPlanFactory.isConfident(decision.confidence())) {
            return Optional.empty();
        }
        if (!decision.missingParameters().isEmpty()) {
            return Optional.of(new CartConversationHandler.Response(
                    "Para continuar necesito estos datos: "
                            + String.join(", ", decision.missingParameters()) + "."));
        }
        CartCommandParser.Action action = toCartAction(decision);
        if (action == null) {
            return Optional.empty();
        }

        CartCommandParser.Command deterministicCommand = CartCommandParser.parse(context.latestMessage());
        CatalogQuery effectiveQuery = decision.catalogQuery();
        int effectiveQuantity = decision.quantity();
        if ((action == CartCommandParser.Action.ADD || action == CartCommandParser.Action.REMOVE)
                && deterministicCommand.action() == action
                && deterministicCommand.query() != null
                && !deterministicCommand.query().isEmpty()
                && deterministicCommand.query().hasPrimarySelector()) {
            effectiveQuery = deterministicCommand.query();
            effectiveQuantity = deterministicCommand.quantity();
        }
        try {
            return cartConversationHandler.handle(
                    context,
                    new CartCommandParser.Command(action, effectiveQuery, effectiveQuantity));
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "CONVERSATIONAL_CART_FAILED", java.util.Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "action", decision.action().name()));
            return Optional.of(new CartConversationHandler.Response(SAFE_FALLBACK));
        }
    }

    public ConversationRenderedResponse execute(ConversationContext context) {
        return cartConversationHandler.handle(context)
                .map(response -> ConversationRenderedResponse.text(response.text()))
                .orElse(ConversationRenderedResponse.text(SAFE_FALLBACK));
    }

    private static CartCommandParser.Action toCartAction(ConversationIntentDecision decision) {
        return switch (decision.action()) {
            case ADD_TO_CART -> CartCommandParser.Action.ADD;
            case VIEW_CART -> CartCommandParser.Action.VIEW;
            case REMOVE_FROM_CART -> CartCommandParser.Action.REMOVE;
            case CLEAR_CART -> CartCommandParser.Action.CLEAR;
            case REVIEW_CHECKOUT -> CartCommandParser.Action.REVIEW_CHECKOUT;
            case CONFIRM_CHECKOUT -> CartCommandParser.Action.CONFIRM;
            case CANCEL_CHECKOUT -> CartCommandParser.Action.CANCEL_CHECKOUT;
            default -> null;
        };
    }

    private static boolean isImplicitCartAddition(ConversationContext context) {
        if (context == null || !CartCommandParser.isImplicitAddRequest(context.latestMessage())) {
            return false;
        }
        // "También quiero ..." is a cart mutation only when the bounded
        // conversation already contains a cart command. Without this guard,
        // a normal catalog request could unexpectedly create a cart.
        return context.recentMessages().stream()
                .filter(message -> !java.util.Objects.equals(message, context.latestMessage()))
                .map(CartCommandParser::parse)
                .anyMatch(command -> command.action() == CartCommandParser.Action.ADD
                        || command.action() == CartCommandParser.Action.REMOVE
                        || command.action() == CartCommandParser.Action.VIEW
                        || command.action() == CartCommandParser.Action.REVIEW_CHECKOUT
                        || command.action() == CartCommandParser.Action.CONFIRM
                        || command.action() == CartCommandParser.Action.CANCEL_CHECKOUT);
    }
}
