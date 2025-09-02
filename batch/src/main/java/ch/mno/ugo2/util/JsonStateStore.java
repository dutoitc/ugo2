package ch.mno.ugo2.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Minimal JSON state store for last-sent metrics and ETags. */
public class JsonStateStore {
    private static final ObjectMapper M = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Path file;
    private Map<String,Object> root = new HashMap<>();

    public JsonStateStore(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) {
                root = M.readValue(Files.readAllBytes(file), new TypeReference<>(){});
            }
        } catch (IOException e) {
            root = new HashMap<>();
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> section(String name) {
        return (Map<String,Object>) root.computeIfAbsent(name, k -> new HashMap<>());
    }
    public String getEtag(String key){ Object v = section("etag").get(key); return v==null?null:String.valueOf(v); }
    public void setEtag(String key, String etag){ section("etag").put(key, etag); }

    @SuppressWarnings("unchecked")
    public Map<String,Object> getVideoState(String videoId){
        Map<String,Object> map = (Map<String,Object>) section("video").get(videoId);
        if (map == null) { map = new HashMap<>(); section("video").put(videoId, map); }
        return map;
    }

    public void put(String section, String key, Object value){ section(section).put(key, value); }
    public Object get(String section, String key){ return section(section).get(key); }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            M.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
