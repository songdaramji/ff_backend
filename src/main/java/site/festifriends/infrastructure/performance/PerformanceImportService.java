package site.festifriends.infrastructure.performance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final KopisClient kopisClient;
    private final PerformanceRepository performanceRepository;

    @Transactional
    public ImportResult importYear(int year) {
        if (!kopisClient.configured()) {
            throw new IllegalStateException("KOPIS_SERVICE_KEY is not configured");
        }

        LocalDate cursor = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        int scanned = 0;
        int matched = 0;
        int saved = 0;
        int updated = 0;

        while (!cursor.isAfter(yearEnd)) {
            LocalDate chunkEnd = cursor.plusDays(30);
            if (chunkEnd.isAfter(yearEnd)) chunkEnd = yearEnd;

            List<String> ids = kopisClient.findMatchingIds(cursor, chunkEnd);
            scanned += ids.size();
            for (String id : ids) {
                KopisPerformance item = kopisClient.getDetail(id);
                if (!item.title().contains("페스티벌") || !"대중음악".equals(item.genre())) {
                    continue;
                }
                matched++;
                var existing = performanceRepository.findByKopisId(item.id());
                if (existing.isPresent()) {
                    existing.get().updateSeedDefaults(item.runtime(), item.age(), item.poster());
                    updated++;
                    continue;
                }
                performanceRepository.save(toEntity(item));
                saved++;
            }
            cursor = chunkEnd.plusDays(1);
        }
        return new ImportResult(scanned, matched, saved, updated);
    }

    private Performance toEntity(KopisPerformance source) {
        LocalDate start = LocalDate.parse(source.startDate(), SEED_DATE);
        LocalDate end = LocalDate.parse(source.endDate(), SEED_DATE);
        Performance entity = Performance.builder()
            .kopisId(source.id()).genre(source.genre()).title(source.title())
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

    public record ImportResult(int scanned, int matched, int saved, int updated) { }
}
