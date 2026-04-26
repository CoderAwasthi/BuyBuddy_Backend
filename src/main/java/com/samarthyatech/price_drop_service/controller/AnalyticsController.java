package com.samarthyatech.price_drop_service.controller;

import com.samarthyatech.price_drop_service.model.ClickEvent;
import com.samarthyatech.price_drop_service.model.AnalyticsIngestRequest;
import com.samarthyatech.price_drop_service.model.AnalyticsIngestResponse;
import com.samarthyatech.price_drop_service.model.AnalyticsEvent;
import com.samarthyatech.price_drop_service.model.TopProductResponse;
import com.samarthyatech.price_drop_service.repo.ClickEventRepo;
import com.samarthyatech.price_drop_service.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ClickEventRepo repo;
    private final AnalyticsService analyticsService;

    // ── Ingest ───────────────────────────────────────────────────────────────
    @PostMapping("/analytics")
    public Mono<AnalyticsIngestResponse> ingest(@RequestBody Mono<AnalyticsIngestRequest> requestMono) {
        return requestMono.flatMap(analyticsService::ingest);
    }

    // ── Events count (filtered) ──────────────────────────────────────────────
    @GetMapping(value = "/analytics/events/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Long> totalIngestedEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String userId) {

        boolean hasFilter = from != null || to != null || platform != null || eventName != null || userId != null;
        if (!hasFilter) return analyticsService.totalIngestedEvents();
        return analyticsService.countFilteredEvents(toInstant(from, false), toInstant(to, true), platform, eventName, userId);
    }

    // ── Recent events (filtered) ─────────────────────────────────────────────
    @GetMapping(value = "/analytics/events/recent", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<AnalyticsEvent> recentEvents(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String userId) {
        return analyticsService.recentEvents(limit, toInstant(from, false), toInstant(to, true), platform, eventName, userId);
    }

    // ── Top event names (filtered) ───────────────────────────────────────────
    @GetMapping(value = "/analytics/events/top-names", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<Map<String, Object>> topEventNames(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String userId) {
        return analyticsService.topEventNames(limit, toInstant(from, false), toInstant(to, true), platform, userId);
    }

    // ── Distinct filter option values ────────────────────────────────────────
    @GetMapping(value = "/analytics/events/platforms", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<String> distinctPlatforms() {
        return analyticsService.recentEvents(1000, null, null, null, null, null)
                .mapNotNull(e -> e.getPayload() != null ? (String) e.getPayload().get("platform") : null)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sort();
    }

    @GetMapping(value = "/analytics/events/names", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<String> distinctEventNames() {
        return analyticsService.recentEvents(1000, null, null, null, null, null)
                .mapNotNull(AnalyticsEvent::getEventName)
                .filter(n -> !n.isBlank())
                .distinct()
                .sort();
    }

    // ── Total affiliate clicks (date-filtered) ────────────────────────────────
    @GetMapping(value = "/analytics/clicks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Long> totalClicks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return clickFlux(from, to).count();
    }

    // ── Top products (date-filtered) ──────────────────────────────────────────
    @GetMapping(value = "/analytics/top-products", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<TopProductResponse> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return clickFlux(from, to)
                .groupBy(ClickEvent::getAsin)
                .flatMap(group -> group.count().map(count -> new TopProductResponse(group.key(), count)))
                .sort((a, b) -> Long.compare(b.getClicks(), a.getClicks()))
                .take(10);
    }

    // ── Daily clicks (date-filtered, sorted) ──────────────────────────────────
    @GetMapping(value = "/analytics/daily", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<Map<String, Object>> dailyClicks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return clickFlux(from, to)
                .groupBy(e -> e.getCreatedAt().toLocalDate())
                .flatMap(group -> group.count().map(count -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", group.key().toString());
                    row.put("clicks", count);
                    return row;
                }))
                .sort(Comparator.comparing(r -> r.get("date").toString()));
    }

    // ── Helper: pick click stream by date range ───────────────────────────────
    private Flux<ClickEvent> clickFlux(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return repo.findByCreatedAtBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        } else if (from != null) {
            return repo.findByCreatedAtAfter(from.atStartOfDay());
        } else if (to != null) {
            return repo.findByCreatedAtBefore(to.plusDays(1).atStartOfDay());
        }
        return repo.findAll();
    }

    // ── Helper: LocalDate → Instant ───────────────────────────────────────────
    private static Instant toInstant(LocalDate d, boolean endOfDay) {
        if (d == null) return null;
        LocalDateTime ldt = endOfDay ? d.plusDays(1).atStartOfDay() : d.atStartOfDay();
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
