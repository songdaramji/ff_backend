package site.festifriends.infrastructure.performance;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SpotifyClient {
    @Qualifier("oAuthRestClient")
    private final RestClient restClient;

    @Value("${app.performance-import.spotify-client-id:}") private String clientId;
    @Value("${app.performance-import.spotify-client-secret:}") private String clientSecret;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public List<String> findArtistGenres(String artistName) {
        String encoded = URLEncoder.encode("artist:" + artistName, StandardCharsets.UTF_8);
        JsonNode response = restClient.get()
            .uri("https://api.spotify.com/v1/search?q=" + encoded + "&type=artist&limit=5")
            .headers(headers -> headers.setBearerAuth(token()))
            .retrieve().body(JsonNode.class);
        if (response == null) return List.of();
        JsonNode items = response.path("artists").path("items");
        for (JsonNode artist : items) {
            if (normalize(artist.path("name").asText()).equals(normalize(artistName))) {
                List<String> genres = new ArrayList<>();
                artist.path("genres").forEach(genre -> genres.add(genre.asText()));
                return genres;
            }
        }
        return List.of();
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(expiresAt)) return accessToken;
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        JsonNode response = restClient.post().uri("https://accounts.spotify.com/api/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Authorization", "Basic " + credentials)
            .body("grant_type=client_credentials").retrieve().body(JsonNode.class);
        if (response == null || response.path("access_token").isMissingNode()) {
            throw new IllegalStateException("Spotify token response is invalid");
        }
        accessToken = response.path("access_token").asText();
        expiresAt = Instant.now().plusSeconds(Math.max(60, response.path("expires_in").asLong(3600) - 60));
        return accessToken;
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
