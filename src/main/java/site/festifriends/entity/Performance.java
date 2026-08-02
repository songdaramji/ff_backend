package site.festifriends.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import site.festifriends.common.model.SoftDeleteEntity;
import site.festifriends.entity.enums.PerformanceState;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "performance")
public class Performance extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id", nullable = false)
    private Long id;

    @Column(name = "kopis_id", unique = true, length = 20)
    @Comment("KOPIS 공연 ID")
    private String kopisId;

    @Column(name = "genre", length = 100)
    @Comment("KOPIS 공연 장르")
    private String genre;

    @Column(name = "title", nullable = false)
    @Comment("공연명")
    private String title;

    @Column(name = "start_date", nullable = false)
    @Comment("공연 시작 날짜")
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    @Comment("공연 종료 날짜")
    private LocalDateTime endDate;

    @Column(name = "location", nullable = false)
    @Comment("공연 장소")
    private String location;

    @ElementCollection
    @CollectionTable(name = "performance_cast", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "cast_member")
    @Comment("공연 출연진")
    private List<String> cast = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "performance_crew", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "crew_member")
    @Comment("공연 제작진")
    private List<String> crew = new ArrayList<>();

    @Column(name = "runtime")
    @Comment("공연 런타임")
    private String runtime;

    @Column(name = "age")
    @Comment("관람 연령")
    private String age;

    @ElementCollection
    @CollectionTable(name = "performance_production_company", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "company")
    @Comment("제작사")
    private List<String> productionCompany = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "performance_agency", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "agency")
    @Comment("기획사")
    private List<String> agency = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "performance_host", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "host")
    @Comment("주최")
    private List<String> host = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "performance_organizer", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "organizer")
    @Comment("주관")
    private List<String> organizer = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "performance_price", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "price")
    @Comment("티켓 가격")
    private List<String> price = new ArrayList<>();

    @Column(name = "poster_url")
    @Comment("포스터 이미지 URL")
    private String poster;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    @Comment("공연 상태(공연 예정/공연 중/공연 종료)")
    private PerformanceState state;

    @Column(name = "visit", nullable = false)
    @Comment("내한 여부")
    private String visit;

    @ElementCollection
    @CollectionTable(name = "performance_time", joinColumns = @JoinColumn(name = "performance_id"))
    @OrderColumn(name = "order_index")
    @Column(name = "time")
    @Comment("공연 시간")
    private List<String> time = new ArrayList<>();

    @OneToMany(mappedBy = "performance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Comment("소개 이미지 목록")
    private List<PerformanceImage> imgs = new ArrayList<>();

    @Builder
    public Performance(String kopisId, String genre, String title, LocalDateTime startDate, LocalDateTime endDate, String location,
        List<String> cast, List<String> crew, String runtime, String age,
        List<String> productionCompany, List<String> agency, List<String> host,
        List<String> organizer, List<String> price, String poster,
        PerformanceState state, String visit, List<String> time) {
        this.kopisId = kopisId;
        this.genre = genre;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
        if (cast != null) {
            this.cast = cast;
        }
        if (crew != null) {
            this.crew = crew;
        }
        this.runtime = runtime;
        this.age = age;
        if (productionCompany != null) {
            this.productionCompany = productionCompany;
        }
        if (agency != null) {
            this.agency = agency;
        }
        if (host != null) {
            this.host = host;
        }
        if (organizer != null) {
            this.organizer = organizer;
        }
        if (price != null) {
            this.price = price;
        }
        this.poster = poster;
        this.state = state;
        this.visit = visit;
        if (time != null) {
            this.time = time;
        }
    }

    public void addImage(String src, String alt) {
        if (src == null || src.isBlank()) {
            return;
        }
        this.imgs.add(PerformanceImage.builder()
            .performance(this)
            .src(src)
            .alt(alt)
            .build());
    }

    public void updateState() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(this.startDate)) {
            this.state = PerformanceState.UPCOMING;
        } else if (now.isAfter(this.endDate)) {
            this.state = PerformanceState.COMPLETED;
        } else {
            this.state = PerformanceState.ONGOING;
        }
    }

    public void updateSeedDefaults(String runtime, String age, String poster) {
        this.runtime = runtime;
        this.age = age;
        this.poster = poster;
    }
}
