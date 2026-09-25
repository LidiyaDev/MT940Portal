package com.bank.mt940portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Produces a short human readable summary of what a maker changed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffUtil {

    private static final int MAX_CHANGES = 25;

    private final ObjectMapper objectMapper;

    public String summarise(String currentJson, String payloadJson) {
        if (currentJson == null || currentJson.isBlank()) {
            return "New record";
        }
        try {
            JsonNode before = objectMapper.readTree(currentJson);
            JsonNode after = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
            List<String> changes = new ArrayList<>();
            collect(before, after, "", changes);
            if (changes.isEmpty()) {
                return "No field changes";
            }
            if (changes.size() > MAX_CHANGES) {
                return String.join("; ", changes.subList(0, MAX_CHANGES)) + "; ...";
            }
            return String.join("; ", changes);
        } catch (Exception ex) {
            log.warn("Unable to diff approval payload", ex);
            return "Updated";
        }
    }

    private void collect(JsonNode before, JsonNode after, String path, List<String> changes) {
        Iterator<String> fieldNames = after.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode afterValue = after.get(field);
            JsonNode beforeValue = before.get(field);
            String key = path.isEmpty() ? field : path + "." + field;

            if (afterValue.isObject() && beforeValue != null && beforeValue.isObject()) {
                collect(beforeValue, afterValue, key, changes);
            } else if (beforeValue == null) {
                changes.add(key + ": (none) -> " + compact(afterValue));
            } else if (!beforeValue.equals(afterValue)) {
                changes.add(key + ": " + compact(beforeValue) + " -> " + compact(afterValue));
            }
        }
    }

    private String compact(JsonNode node) {
        String value = node.isTextual() ? node.asText() : node.toString();
        if (value.length() > 60) {
            return value.substring(0, 57) + "...";
        }
        return value;
    }
}
