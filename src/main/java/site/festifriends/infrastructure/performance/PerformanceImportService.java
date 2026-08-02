package site.festifriends.infrastructure.performance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.festifriends.domain.performance.repository.PerformanceRepository;
import site.festifriends.entity.Performance;
import site.festifriends.entity.enums.PerformanceState;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceImportService {
    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final KopisClient kopisClient;
    private final SpotifyClient spotifyClient;
    private final PerformanceRepository performanceRepository;

    @Value("${app.performance-import.rock-genres}")
    private String rockGenres;

    public ImportResult importYear(int year) {
        if (!kopisClient.configured() || !spotifyClient.configured()) {
            throw new IllegalStateException("KOPIS_SERVICE_KEY and Spotify credentials are required");
        }
        int scanned = 0;
        int rock = 0;
        int saved = 0;
        LocalDate cursor = Year.of(year).atDay(1);
        LocalDate lastDay = Year.of(year).atMonth(12).atEndOfMonth();

        while (!cursor.isAfter(lastDay)) {
            LocalDate rangeEnd = cursor.plusDays(30);
            if (rangeEnd.isAfter(lastDay)) rangeEnd = lastDay;
            List<String> ids = kopisClient.findPopularMusicIds(cursor, rangeEnd);
            for (String id : ids) {
                scanned++;
                if (performanceRepository.findByKopisId(id).isPresent()) continue;
                try {
                    KopisPerformance item = kopisClient.getDetail(id);
                    if (!isRock(item.cast())) continue;
                    rock++;
                    performanceRepository.save(toEntity(item));
                    saved++;
                } catch (Exception e) {
                    log.warn("KOPIS performance import failed. id={}", id, e);
                }
            }
            cursor = rangeEnd.plusDays(1);
        }
        return new ImportResult(scanned, rock, saved);
    }

    private boolean isRock(List<String> cast) {
        List<String> keywords = Arrays.stream(rockGenres.toLowerCase(Locale.ROOT).split(","))
            .map(String::trim).filter(value -> !value.isBlank()).toList();
        for (String artist : cast) {
            try {
                if (spotifyClient.findArtistGenres(artist).stream()
                    .map(genre -> genre.toLowerCase(Locale.ROOT))
                    .anyMatch(genre -> keywords.stream().anyMatch(genre::contains))) return true;
            } catch (Exception e) {
                log.warn("Spotify artist lookup failed. artist={}", artist, e);
            }
        }
        return false;
    }

    private Performance toEntity(KopisPerformance source) {
        LocalDate start = LocalDate.parse(source.startDate(), KOPIS_DATE);
        LocalDate end = LocalDate.parse(source.endDate(), KOPIS_DATE);
        Performance entity = Performance.builder()
            .kopisId(source.id()).genre(source.genre()).title(source.title())
            .startDate(LocalDateTime.of(start, LocalTime.MIN))
            .endDate(LocalDateTime.of(end, LocalTime.MAX))
            .location(source.location()).cast(source.cast()).crew(source.crew())
            .runtime(source.runtime()).age(source.age())
            .productionCompany(source.productionCompany()).agency(source.agency())
            .host(source.host()).organizer(source.organizer()).price(source.price())
            .poster(source.poster()).state(state(start, end))
            .visit("Y".equalsIgnoreCase(source.visit()) ? "내한" : "국내")
            .time(source.time()).build();
        int index = 1;
        for (String image : source.images()) entity.addImage(image, source.title() + " 소개 이미지 " + index++);
        return entity;
    }

    private PerformanceState state(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(start)) return PerformanceState.UPCOMING;
        if (today.isAfter(end)) return PerformanceState.COMPLETED;
        return PerformanceState.ONGOING;
    }

    public record ImportResult(int scanned, int rock, int saved) { }
}
