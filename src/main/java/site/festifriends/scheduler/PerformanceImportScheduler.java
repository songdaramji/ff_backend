package site.festifriends.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
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
    public void importAtStartup() { runImport(); }

    @Scheduled(cron = "${app.performance-import.cron:0 30 3 * * *}")
    public void importDaily() { runImport(); }

    private void runImport() {
        if (!enabled) return;
        try {
            var result = importService.importYear(year);
            log.info("Local performance seed import complete. year={}, scanned={}, rock={}, saved={}",
                year, result.scanned(), result.rock(), result.saved());
        } catch (Exception e) {
            log.error("Local performance seed import failed. year={}", year, e);
        }
    }
}
