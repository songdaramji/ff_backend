package site.festifriends.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import site.festifriends.infrastructure.performance.PerformanceImportService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceImportScheduler {
    private final PerformanceImportService importService;

    @Value("${app.performance-import.enabled:false}") private boolean enabled;
    @Value("${app.performance-import.year:2026}") private int year;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void importAtStartup() { runImport(); }

    @Scheduled(cron = "${app.performance-import.cron:0 30 3 * * *}")
    public void importDaily() { runImport(); }

    private void runImport() {
        if (!enabled) return;
        try {
            var result = importService.importYear(year);
            log.info("KOPIS festival performance import complete. year={}, scanned={}, matched={}, saved={}, updated={}",
                year, result.scanned(), result.matched(), result.saved(), result.updated());
        } catch (Exception e) {
            log.error("KOPIS festival performance import failed. year={}", year, e);
        }
    }
}
