package com.impati.commerce.display.application.port.in;

import java.util.List;

/** 지면의 한 구획. 담긴 카드가 없으면 지면에 나오지 않는다. */
public record HomeSection(String key, String title, List<HomeProductCard> products) {
}
