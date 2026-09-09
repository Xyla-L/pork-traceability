package com.pork.core.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** Generates collision-resistant display numbers; database unique keys remain authoritative. */
public final class BusinessNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private BusinessNoGenerator() {
    }

    public static String next(String prefix) {
        return prefix + DATE.format(LocalDate.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
