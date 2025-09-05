package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightValue(JsonNode value) {

    /** Renvoie la valeur numérique si c'est un nombre, sinon null. */
    public Integer asIntOrNull() {
        return value != null && value.isNumber() ? value.intValue() : null;
    }

    /** Renvoie la map (ex. REACTION_LIKE → 24) si c'est un objet, sinon null. */
    public Map<String, Integer> asMapOrNull() {
        if (value == null || !value.isObject()) return null;
        Map<String, Integer> out = new LinkedHashMap<>();
        value.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asInt()));
        return out;
    }
}