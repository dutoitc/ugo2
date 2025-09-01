package ch.mno.ugo2.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Jsons {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    public static byte[] toBytes(Object o) {
        try { return MAPPER.writeValueAsBytes(o); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
