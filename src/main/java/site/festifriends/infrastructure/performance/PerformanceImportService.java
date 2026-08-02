package site.festifriends.infrastructure.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.festifriends.domain.performance.repository.PerformanceRepository;
import site.festifriends.entity.Performance;
import site.festifriends.entity.enums.PerformanceState;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceImportService {
    private static final DateTimeFormatter SEED_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final ObjectMapper objectMapper;
    private final PerformanceRepository performanceRepository;

    /**
     * Imports the curated local seed without calling KOPIS or Spotify.
     * The generated kopisId is unique, so repeated application starts are idempotent.
     */
    @Transactional
    public ImportResult importYear(int year) {
        List<SeedPerformance> items = readSeed(year);
        int saved = 0;
        for (SeedPerformance item : items) {
            if (performanceRepository.findByKopisId(item.kopisId()).isPresent()) {
                continue;
            }
            Performance entity = toEntity(item);
            performanceRepository.save(entity);
            saved++;
        }
        return new ImportResult(items.size(), items.size(), saved);
    }

    private List<SeedPerformance> readSeed(int year) {
        ClassPathResource resource = new ClassPathResource("seed/performances-" + year + ".json");
        if (!resource.exists()) {
            throw new IllegalStateException("Performance seed not found for year " + year);
        }
        try (var input = resource.getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read performance seed for year " + year, e);
        }
    }

    private Performance toEntity(SeedPerformance source) {
        LocalDate start = LocalDate.parse(source.startDate(), SEED_DATE);
        LocalDate end = LocalDate.parse(source.endDate(), SEED_DATE);
        Performance entity = Performance.builder()
            .kopisId(source.kopisId()).genre(source.genre()).title(source.title())
            .startDate(LocalDateTime.of(start, LocalTime.MIN))
            .endDate(LocalDateTime.of(end, LocalTime.MAX))
            .location(source.location()).cast(source.cast()).crew(source.crew())
            .runtime(source.runtime()).age(source.age())
            .productionCompany(source.productionCompany()).agency(source.agency())
            .host(source.host()).organizer(source.organizer()).price(source.price())
            .poster(source.poster()).state(state(start, end)).visit(source.visit())
            .time(source.time()).build();
        int index = 1;
        for (String image : source.images()) {
            entity.addImage(image, source.title() + " 소개 이미지 " + index++);
        }
        return entity;
    }

    private PerformanceState state(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(start)) return PerformanceState.UPCOMING;
        if (today.isAfter(end)) return PerformanceState.COMPLETED;
        return PerformanceState.ONGOING;
    }

    public record ImportResult(int scanned, int rock, int saved) { }

    private record SeedPerformance(
        String kopisId, String genre, String title, String startDate, String endDate,
        String location, List<String> cast, List<String> crew, String runtime, String age,
        List<String> productionCompany, List<String> agency, List<String> host,
        List<String> organizer, List<String> price, String poster, String visit,
        List<String> time, List<String> images
    ) { }
}
