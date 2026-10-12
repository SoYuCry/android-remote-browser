package io.github.soyucy.androidremote.companion;

import java.time.*;
import java.util.Random;

/** Calendar policy independent of the device's selected timezone. */
final class ScheduleWindow {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static LocalDate date(long now) { return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate(); }
    static long start(LocalDate day) { return day.atTime(17, 45).atZone(ZONE).toInstant().toEpochMilli(); }
    static long end(LocalDate day) { return day.atTime(18, 5).atZone(ZONE).toInstant().toEpochMilli(); }
    static boolean weekday(LocalDate day) { return day.getDayOfWeek().getValue() <= 5; }
    static boolean inWindow(long now) {
        LocalDate day = date(now);
        return weekday(day) && now >= start(day) && now < end(day);
    }
    static long next(long now, String handledThrough, Random random) {
        LocalDate day = date(now);
        while (!weekday(day) || day.toString().compareTo(handledThrough) <= 0 || now >= end(day)) day = day.plusDays(1);
        long earliest = Math.max(start(day), ((now + 999) / 1000) * 1000);
        if (earliest >= end(day)) return next(end(day), handledThrough, random);
        int seconds = (int) ((end(day) - earliest) / 1000);
        return earliest + random.nextInt(seconds) * 1000L;
    }
}
