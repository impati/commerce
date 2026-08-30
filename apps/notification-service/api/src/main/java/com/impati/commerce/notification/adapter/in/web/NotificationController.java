package com.impati.commerce.notification.adapter.in.web;

import com.impati.commerce.common.ApiContracts.NotificationResponse;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationUseCase notificationUseCase;

    public NotificationController(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @GetMapping
    List<NotificationResponse> list(@RequestParam String memberId) {
        return notificationUseCase.listFor(memberId).stream().map(NotificationResponseMapper::from).toList();
    }
}
