package site.festifriends.infrastructure.performance;

import java.util.List;

public record KopisPerformance(
    String id, String title, String startDate, String endDate, String location,
    List<String> cast, List<String> crew, String runtime, String age,
    List<String> productionCompany, List<String> agency, List<String> host,
    List<String> organizer, List<String> price, String poster, String state,
    String visit, List<String> time, List<String> images, String genre
) {
}
