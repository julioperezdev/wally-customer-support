package com.wally.customersupport.conversation.application.tool;

/**
 * Structured contract used by Bedrock to propose one bounded conversation
 * route. It is an inference contract, not a business capability: the backend
 * still validates the proposal and the workflow router decides what executes.
 */
public final class ConversationRouteToolContract {

    public static final String NAME = "conversation.route";

    public static final WcsToolDescriptor DESCRIPTOR = new WcsToolDescriptor(
            NAME,
            "Clasifica el mensaje en un caso de uso WCS y extrae únicamente argumentos estructurados. No ejecuta ninguna operación.",
            "conversation-route-input-v2",
            """
                    {"type":"object","properties":{
                      "intent":{"type":"string","enum":["UNKNOWN","GREETING","CATALOG_SEARCH","PURCHASE_LINK","BUSINESS_HOURS","POLICY_QUERY","GENERAL_SUPPORT","HUMAN_HANDOFF"]},
                      "action":{"type":"string","enum":["NONE","UNKNOWN","GREETING","CATALOG_SEARCH","ADD_TO_CART","VIEW_CART","REMOVE_FROM_CART","CLEAR_CART","REVIEW_CHECKOUT","CONFIRM_CHECKOUT","CANCEL_CHECKOUT","PURCHASE_LINK","BUSINESS_HOURS","POLICY_QUERY","HUMAN_HANDOFF","GENERAL_SUPPORT"]},
                      "confidence":{"type":"number","minimum":0,"maximum":1},
                      "quantity":{"type":"integer","minimum":1,"maximum":100},
                      "catalogQuery":{"type":["object","null"],"properties":{
                        "name":{"type":["string","null"]},
                        "sku":{"type":["string","null"]},
                        "size":{"type":["string","null"]},
                        "color":{"type":["string","null"]},
                        "productType":{"type":["string","null"]},
                        "minPrice":{"type":["number","null"],"minimum":0},
                        "maxPrice":{"type":["number","null"],"minimum":0}
                      },"required":["name","sku","size","color","productType","minPrice","maxPrice"],"additionalProperties":false},
                      "policyKey":{"type":["string","null"],"enum":["shipping","payments","changes","returns",null]},
                      "missingParameters":{"type":"array","items":{"type":"string","maxLength":32},"maxItems":8}
                    },"required":["intent","action","confidence","quantity","catalogQuery","policyKey","missingParameters"],"additionalProperties":false}
                    """.replaceAll("\\s+", ""),
            "conversation-route-output-v1",
            "{\"type\":\"object\",\"properties\":{\"validated\":{\"type\":\"boolean\"},\"workflow\":{\"type\":\"string\"}},\"required\":[\"validated\",\"workflow\"],\"additionalProperties\":false}",
            "conversation.route");

    private ConversationRouteToolContract() {
    }
}
