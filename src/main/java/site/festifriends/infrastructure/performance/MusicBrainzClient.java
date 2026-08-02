package site.festifriends.infrastructure.performance;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MusicBrainzClient {
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 1_100;

    @Qualifier("oAuthRestClient")
    private final RestClient restClient;

    @Value("${app.performance-import.musicbrainz-user-agent}")
    private String userAgent;

    private final Map<String, List<String>> genreCache = new ConcurrentHashMap<>();
    private long lastRequestAt;

    public List<String> findArtistGenres(String artistName) {
        String key = normalize(artistName);
        return genreCache.computeIfAbsent(key, ignored -> searchGenres(artistName));
    }

    private List<String> searchGenres(String artistName) {
        waitForRateLimit();
        String query = URLEncoder.encode("artist:\"" + artistName + "\"", StandardCharsets.UTF_8);
        JsonNode response = restClient.get()
            .uri("https://musicbrainz.org/ws/2/artist/?query=" + query + "&fmt=json&limit=10")
            .header("User-Agent", userAgent)
            .retrieve().body(JsonNode.class);
        if (response == null) return List.of();

        for (JsonNode artist : response.path("artists")) {
            if (!matchesName(artist, artistName)) continue;
            List<String> values = new ArrayList<>();
            artist.path("genres").forEach(genre -> values.add(genre.path("name").asText()));
            artist.path("tags").forEach(tag -> values.add(tag.path("name").asText()));
            return values.stream().filter(value -> !value.isBlank()).distinct().toList();
        }
        return List.of();
    }

    private boolean matchesName(JsonNode artist, String requestedName) {
        String requested = normalize(requestedName);
        if (normalize(artist.path("name").asText()).equals(requested)) return true;
        if (normalize(artist.path("sort-name").asText()).equals(requested)) return true;
        for (JsonNode alias : artist.path("aliases")) {
            if (normalize(alias.path("name").asText()).equals(requested)) return true;
            if (normalize(alias.path("sort-name").asText()).equals(requested)) return true;
        }
        return false;
    }

    private synchronized void waitForRateLimit() {
        long waitMillis = MIN_REQUEST_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestAt);
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("MusicBrainz request interrupted", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
