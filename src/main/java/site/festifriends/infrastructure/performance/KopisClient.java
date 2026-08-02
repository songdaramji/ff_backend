package site.festifriends.infrastructure.performance;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
@RequiredArgsConstructor
public class KopisClient {
    private static final String BASE_URL = "http://www.kopis.or.kr/openApi/restful";
    private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Qualifier("oAuthRestClient")
    private final RestClient restClient;

    @Value("${app.performance-import.kopis-service-key:}")
    private String serviceKey;

    public List<String> findPopularMusicIds(LocalDate from, LocalDate to) {
        List<String> ids = new ArrayList<>();
        for (int page = 1; ; page++) {
            String xml = restClient.get().uri(BASE_URL + "/pblprfr?service={key}&stdate={from}&eddate={to}&cpage={page}&rows=100&shcate=CCCD",
                serviceKey, from.format(QUERY_DATE), to.format(QUERY_DATE), page).retrieve().body(String.class);
            Document document = parse(xml);
            NodeList performances = document.getElementsByTagName("db");
            if (performances.getLength() == 0) break;
            for (int i = 0; i < performances.getLength(); i++) {
                ids.add(text((Element) performances.item(i), "mt20id"));
            }
            if (performances.getLength() < 100) break;
        }
        return ids;
    }

    public KopisPerformance getDetail(String id) {
        String xml = restClient.get().uri(BASE_URL + "/pblprfr/{id}?service={key}", id, serviceKey)
            .retrieve().body(String.class);
        Element db = (Element) parse(xml).getElementsByTagName("db").item(0);
        if (db == null) throw new IllegalStateException("KOPIS detail not found: " + id);
        return new KopisPerformance(id, text(db, "prfnm"), text(db, "prfpdfrom"), text(db, "prfpdto"),
            text(db, "fcltynm"), split(text(db, "prfcast")), split(text(db, "prfcrew")),
            text(db, "prfruntime"), text(db, "prfage"), split(text(db, "entrpsnmP")),
            split(text(db, "entrpsnmA")), split(text(db, "entrpsnmH")), split(text(db, "entrpsnmS")),
            split(text(db, "pcseguidance")), text(db, "poster"), text(db, "prfstate"), text(db, "visit"),
            split(text(db, "dtguidance")), childTexts(db, "styurl"), text(db, "genrenm"));
    }

    public boolean configured() { return serviceKey != null && !serviceKey.isBlank(); }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid KOPIS response", e);
        }
    }

    private String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private List<String> childTexts(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent().trim();
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,\\n]"))
            .map(String::trim).filter(item -> !item.isBlank()).toList();
    }
}
