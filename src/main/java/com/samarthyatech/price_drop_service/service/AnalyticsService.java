package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.AnalyticsEvent;
import com.samarthyatech.price_drop_service.model.AnalyticsEventRequest;
import com.samarthyatech.price_drop_service.model.AnalyticsIngestRequest;
import com.samarthyatech.price_drop_service.model.AnalyticsIngestResponse;
import com.samarthyatech.price_drop_service.repo.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    // ── Ingest ──────────────────────────────────────────────────────────────
    public Mono<AnalyticsIngestResponse> ingest(AnalyticsIngestRequest request) {
        List<AnalyticsEventRequest> events = request != null && request.getEvents() != null
                ? request.getEvents()
                : Collections.emptyList();

        int received = events.size();
        if (received == 0) {
            return Mono.just(new AnalyticsIngestResponse(true, 0, 0, "No events provided"));
        }

        return analyticsEventRepository.saveAll(Flux.fromIterable(events).map(this::toEntity))
                .count()
                .map(processedCount -> {
                    int processed = processedCount.intValue();
                    log.info("Analytics ingest completed. Received: {}, Processed: {}", received, processed);
                    return new AnalyticsIngestResponse(true, received, processed, "Events accepted");
                });
    }

    // ── Unfiltered counts (kept for KPI zero-state) ──────────────────────────
    public Mono<Long> totalIngestedEvents() {
        return analyticsEventRepository.count();
    }

    // ── Filtered total ───────────────────────────────────────────────────────
    public Mono<Long> countFilteredEvents(Instant from, Instant to,
                                          String platform, String eventName, String userId) {
        return mongoTemplate.count(buildEventQuery(from, to, platform, eventName, userId), AnalyticsEvent.class);
    }

    // ── Filtered recent events ───────────────────────────────────────────────
    public Flux<AnalyticsEvent> recentEvents(int limit,
                                             Instant from, Instant to,
                                             String platform, String eventName, String userId) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        Query q = buildEventQuery(from, to, platform, eventName, userId)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(safeLimit);
        return mongoTemplate.find(q, AnalyticsEvent.class);
    }

    // ── Filtered top event names ─────────────────────────────────────────────
    public Flux<Map<String, Object>> topEventNames(int limit,
                                                   Instant from, Instant to,
                                                   String platform, String userId) {
        int safeLimit = Math.min(Math.max(limit, 1), 25);
        Query q = buildEventQuery(from, to, platform, null, userId);

        return mongoTemplate.find(q, AnalyticsEvent.class)
                .groupBy(event -> event.getEventName() == null ? "UNKNOWN" : event.getEventName())
                .flatMap(group -> group.count().map(count -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("eventName", group.key());
                    row.put("count", count);
                    return row;
                }))
                .sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .take(safeLimit);
    }

    // ── Query builder ─────────────────────────────────────────────────────────
    private Query buildEventQuery(Instant from, Instant to,
                                  String platform, String eventName, String userId) {
        List<Criteria> criteria = new ArrayList<>();

        if (from != null || to != null) {
            Criteria ts = Criteria.where("timestamp");
            if (from != null) ts = ts.gte(from);
            if (to != null)   ts = ts.lte(to);
            criteria.add(ts);
        }
        if (platform  != null && !platform.isBlank())  criteria.add(Criteria.where("payload.platform").is(platform));
        if (eventName != null && !eventName.isBlank())  criteria.add(Criteria.where("eventName").is(eventName));
        if (userId    != null && !userId.isBlank())     criteria.add(Criteria.where("payload.userId").is(userId));

        Query q = new Query();
        if (!criteria.isEmpty()) q.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        return q;
    }

    // ── Entity mapper ─────────────────────────────────────────────────────────
    private AnalyticsEvent toEntity(AnalyticsEventRequest event) {
        AnalyticsEvent entity = new AnalyticsEvent();
        entity.setEventId(event.getEventId());
        entity.setEventName(event.getEventName());
        entity.setTimestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        entity.setPayload(event.getPayload());
        return entity;
    }
}
