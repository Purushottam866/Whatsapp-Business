package com.whatsApp.wsp_businessAPI.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateWebhookHandler {

    private final WhatsAppTemplateRepository templateRepository;

    @Transactional
    public void handleTemplateStatus(JsonNode changeValue) {

        if (!changeValue.has("message_template_id")) {
            return;
        }

        String metaTemplateId =
                changeValue.get("message_template_id").asText();

        String event =
                changeValue.has("event")
                        ? changeValue.get("event").asText()
                        : "UNKNOWN";

        log.info("Template status update received. metaId={}, event={}",
                metaTemplateId, event);

        Optional<WhatsAppTemplate> optional =
                templateRepository.findByMetaTemplateId(metaTemplateId);

        if (optional.isEmpty()) {
            log.warn("Template not found in DB for metaTemplateId={}", metaTemplateId);
            return;
        }

        WhatsAppTemplate template = optional.get();

        switch (event.toUpperCase()) {

            case "APPROVED" -> {
                template.setStatus(WhatsAppTemplate.Status.APPROVED);
                log.info("Template APPROVED -> id={}", template.getId());
            }

            case "REJECTED" -> {
                template.setStatus(WhatsAppTemplate.Status.REJECTED);
                log.info("Template REJECTED -> id={}", template.getId());
            }

            case "PAUSED", "DISABLED" -> {
                template.setStatus(WhatsAppTemplate.Status.DISABLED);
                log.info("Template DISABLED -> id={}", template.getId());
            }

            default -> {
                log.info("Unhandled template event: {}", event);
                return;
            }
        }

        templateRepository.save(template);
    }
}
