package com.samarthyatech.price_drop_service.controller;

import com.samarthyatech.price_drop_service.model.ClickEvent;
import com.samarthyatech.price_drop_service.repo.ClickEventRepo;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class AffiliateController {

    private final ClickEventRepo repo;

    public AffiliateController(ClickEventRepo repo) {
        this.repo = repo;
    }

    @GetMapping("/go/{asin}")
    public Mono<Void> redirect(
            @PathVariable String asin,
            @RequestParam String domain,
            @RequestParam(required = false) String userId,
            ServerHttpResponse response
    ) {

        // 🔥 1. SAVE CLICK EVENT
        ClickEvent event = new ClickEvent();
        event.setAsin(asin);
        event.setUserId(userId);
        event.setDomain(domain);
        event.setSource("extension");
        event.setCreatedAt(LocalDateTime.now());

        repo.save(event).subscribe(); // fire & forget

        // 🔥 2. BUILD AFFILIATE URL
        String baseUrl = switch (domain) {
            case "amazon.com" -> "https://www.amazon.com/dp/";
            case "amazon.co.uk" -> "https://www.amazon.co.uk/dp/";
            default -> "https://www.amazon.in/dp/";
        };

        String tag = "yourtag-21";

        String finalUrl = baseUrl + asin +
                "?tag=" + tag +
                "&utm_source=your_extension" +
                "&utm_medium=affiliate" +
                "&utm_campaign=deals";

        // 🔥 3. REDIRECT
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(finalUrl));

        return response.setComplete();
    }
}
