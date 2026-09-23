package com.wally.customersupport.conversation.application.tool;

import java.util.Objects;
import java.util.Optional;

import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.springframework.stereotype.Component;

/**
 * Typed catalog capability. The tool delegates to the existing application
 * service and never executes model-provided SQL or returns unverified facts.
 */
@Component
public class CatalogSearchTool implements WcsTool<CatalogSearchTool.Input, Optional<CatalogSearchResult>> {

    public static final String NAME = "catalog.search";
    public static final WcsToolDescriptor DESCRIPTOR = new WcsToolDescriptor(
            NAME,
            "Busca productos, variantes, precio y stock con filtros comerciales validados.",
            "catalog-input-v1",
            """
                    {"type":"object","properties":{
                      "name":{"type":["string","null"]},
                      "sku":{"type":["string","null"]},
                      "size":{"type":["string","null"]},
                      "color":{"type":["string","null"]},
                      "productType":{"type":["string","null"]},
                      "minPrice":{"type":["number","null"],"minimum":0},
                      "maxPrice":{"type":["number","null"],"minimum":0}
                    },"required":["name","sku","size","color","productType","minPrice","maxPrice"],"additionalProperties":false}
                    """.replaceAll("\\s+", ""),
            "catalog-output-v1",
            """
                    {"type":"object","properties":{
                      "status":{"type":"string"},
                      "resultCount":{"type":"integer","minimum":0},
                      "requestedProductType":{"type":["string","null"]},
                      "followUpKind":{"type":"string"},
                      "reason":{"type":["string","null"]}
                    },"required":["status","resultCount","requestedProductType","followUpKind","reason"],"additionalProperties":false}
                    """.replaceAll("\\s+", ""),
            "catalog.read");

    private final CatalogConversationService catalogConversationService;

    public CatalogSearchTool(CatalogConversationService catalogConversationService) {
        this.catalogConversationService = Objects.requireNonNull(catalogConversationService, "catalogConversationService");
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
    public Optional<CatalogSearchResult> execute(Input input) {
        Objects.requireNonNull(input, "input");
        return catalogConversationService.search(input.query(), java.util.List.of(), input.latestMessage());
    }

    public record Input(CatalogQuery query, String latestMessage) {

        public Input {
            latestMessage = Objects.requireNonNull(latestMessage, "latestMessage");
        }
    }
}
