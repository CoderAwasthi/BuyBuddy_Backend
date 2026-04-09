package com.samarthyatech.price_drop_service.controller;

import com.samarthyatech.price_drop_service.model.Notification;
import com.samarthyatech.price_drop_service.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepo;

    @GetMapping("/alerts")
    public Flux<Notification> getAlerts(@RequestParam String userId) {

        return notificationRepo.findByUserIdAndSentFalse(userId)
                .flatMap(n -> {
                    n.setSent(true);
                    return notificationRepo.save(n);
                });
    }
}