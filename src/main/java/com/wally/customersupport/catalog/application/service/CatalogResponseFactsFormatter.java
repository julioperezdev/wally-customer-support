package com.wally.customersupport.catalog.application.service;

/** Builds the allowlisted factual prompt fields shared by runtime and offline evaluation. */
public final class CatalogResponseFactsFormatter {

    private CatalogResponseFactsFormatter() {
    }

    public static String requiredFacts(CatalogSearchResult result) {
        if (result == null || result.facts().isEmpty()) {
            return "No hay hechos de catálogo para presentar.";
        }
        StringBuilder facts = new StringBuilder();
        for (int index = 0; index < result.facts().size(); index++) {
            var fact = result.facts().get(index);
            facts.append("product[").append(index + 1).append("] ")
                    .append("name=").append(fact.productName())
                    .append(" | sku=").append(fact.sku())
                    .append(" | color=").append(fact.color())
                    .append(" | size=").append(fact.size())
                    .append(" | price=").append(fact.price().toPlainString())
                    .append(" ").append(fact.currency())
                    .append(" | stock=")
                    .append(fact.available() ? fact.stock() : "sin stock")
                    .append('\n');
        }
        return facts.toString().trim();
    }

    public static String approvedKnowledge(CatalogSearchResult result) {
        return CatalogResponseFormatter.render(result);
    }
}
