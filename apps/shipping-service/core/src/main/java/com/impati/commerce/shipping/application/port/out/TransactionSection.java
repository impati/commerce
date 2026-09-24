package com.impati.commerce.shipping.application.port.out;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransactionSection {
    <T> T required(Supplier<T> body);
}
