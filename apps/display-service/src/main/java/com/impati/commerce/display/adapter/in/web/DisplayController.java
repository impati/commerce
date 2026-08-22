package com.impati.commerce.display.adapter.in.web;

import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.display.application.port.in.DisplayUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/display")
public class DisplayController {
    private final DisplayUseCase display;

    public DisplayController(DisplayUseCase display) {
        this.display = display;
    }

    @GetMapping("/home")
    DisplayHomeResponse home() {
        return DisplayResponseMapper.from(display.home());
    }
}

