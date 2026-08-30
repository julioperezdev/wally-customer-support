package com.wally.customersupport.poc.whatsapp;

import java.util.List;

public interface WhatsAppClient {

    void sendText(String recipientWaId, String body);

    void sendTemplate(
            String recipientWaId,
            String templateName,
            String languageCode,
            List<String> bodyTextParameters);
}
