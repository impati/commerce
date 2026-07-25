package com.impati.commerce.display.adapter.in.web;

import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.display.application.DisplayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/display")
public class DisplayController {
    private final DisplayService display;

    public DisplayController(DisplayService display) {
        this.display = display;
    }

    @GetMapping("/home")
    DisplayHomeResponse home() {
        return display.home();
    }
}

