package com.impati.commerce.display.adapter.in.web;

import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.display.application.port.in.DisplayUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/display")
public class DisplayController {
    private final DisplayUseCase displayUseCase;

    public DisplayController(DisplayUseCase displayUseCase) {
        this.displayUseCase = displayUseCase;
    }

    @GetMapping("/home")
    DisplayHomeResponse home() {
        return DisplayResponseMapper.from(displayUseCase.home());
    }
}

