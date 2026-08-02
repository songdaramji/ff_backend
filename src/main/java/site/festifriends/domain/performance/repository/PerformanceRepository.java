package site.festifriends.domain.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import site.festifriends.entity.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long>, PerformanceRepositoryCustom {
    Optional<Performance> findByKopisId(String kopisId);
}
