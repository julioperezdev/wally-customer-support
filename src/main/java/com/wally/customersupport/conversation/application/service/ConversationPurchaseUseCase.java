package com.wally.customersupport.conversation.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.PurchaseLinkCreator;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Owns the conversational purchase boundary: validate one variant and stock,
 * create an idempotent payment link, and render the checkout response.
 */
@Service
public class ConversationPurchaseUseCase {

    private static final String PURCHASE_VARIANT_REQUIRED = "Para generar el link de pago necesito una única "
            + "variante. Indicame el producto, talle y color que querés comprar.";
    private static final String PURCHASE_VARIANT_UNAVAILABLE = "Esa variante no tiene stock disponible en este momento. "
            + "Si querés, puedo mostrarte otras opciones.";
    private static final String PURCHASE_LINK_UNAVAILABLE = "No pude generar el link de pago en este momento. "
            + "Tu pedido no fue confirmado; intentá nuevamente en unos minutos.";

    private final CatalogConversationUseCase catalogConversationUseCase;
    private final PurchaseLinkCreator purchaseLinkCreator;
    private final ConversationExecutionTelemetry telemetry;

    @Autowired
    public ConversationPurchaseUseCase(
            CatalogConversationUseCase catalogConversationUseCase,
            PurchaseLinkCreator purchaseLinkCreator,
            ConversationExecutionTelemetry telemetry) {
        this.catalogConversationUseCase = catalogConversationUseCase;
        this.purchaseLinkCreator = purchaseLinkCreator;
        this.telemetry = telemetry;
    }

    public boolean isDeferral(String message) {
        return CatalogQueryParser.isPurchaseDeferral(message);
    }

    public ConversationRenderedResponse createLink(
            ConversationContext context,
            ConversationIntentDecision decision) {
        Optional<CatalogQuery> query = CatalogQueryParser.parsePurchaseConversation(
                context.recentMessages(), context.latestMessage())
                .or(() -> decision == null
                        ? Optional.empty()
                        : Optional.ofNullable(decision.catalogQuery()))
                .filter(candidate -> !candidate.isEmpty());
        if (query.isEmpty()) {
            telemetry.logPurchaseOutcome(context, "VARIANT_REQUIRED");
            return ConversationRenderedResponse.text(PURCHASE_VARIANT_REQUIRED);
        }

        Optional<CatalogSearchResult> searchResult =
                catalogConversationUseCase.searchFacts(context, query.get());
        if (searchResult.isEmpty()
                || searchResult.get().status() != CatalogSearchResult.Status.MATCHED
                || searchResult.get().facts().size() != 1) {
            telemetry.logPurchaseOutcome(context, "VARIANT_NOT_UNIQUE");
            return ConversationRenderedResponse.text(PURCHASE_VARIANT_REQUIRED);
        }

        var fact = searchResult.get().facts().getFirst();
        int quantity = CatalogQueryParser.purchaseQuantity(context.latestMessage());
        if (!fact.available() || quantity > fact.stock()) {
            telemetry.logPurchaseOutcome(context, "INSUFFICIENT_STOCK");
            return ConversationRenderedResponse.text(PURCHASE_VARIANT_UNAVAILABLE);
        }

        String idempotencyKey = "purchase-" + sha256(
                context.conversationId() + "|" + fact.sku() + "|" + quantity);
        Optional<PurchaseLinkCreator.PurchaseLink> purchaseLink = purchaseLinkCreator.create(
                new PurchaseLinkCreator.CreatePurchaseLinkRequest(
                        context.conversationId(),
                        customerReference(context),
                        fact.sku(),
                        quantity,
                        idempotencyKey));
        if (purchaseLink.isEmpty()) {
            telemetry.logPurchaseOutcome(context, "LINK_UNAVAILABLE");
            return ConversationRenderedResponse.text(PURCHASE_LINK_UNAVAILABLE);
        }

        PurchaseLinkCreator.PurchaseLink link = purchaseLink.get();
        telemetry.logPurchaseOutcome(context, "LINK_CREATED");
        return ConversationRenderedResponse.text(String.format(
                Locale.ROOT,
                "Listo. Preparé tu pedido de %d %s (%s), por un total de %s %s.\n"
                        + "Podés completar el pago acá: %s",
                link.quantity(), link.productName(), link.sku(), link.total(), link.currency(), link.checkoutUrl()));
    }

    private static String customerReference(ConversationContext context) {
        String channel = context.channel() == null ? "unknown" : context.channel().name().toLowerCase(Locale.ROOT);
        return channel + ":" + context.externalCustomerId();
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
