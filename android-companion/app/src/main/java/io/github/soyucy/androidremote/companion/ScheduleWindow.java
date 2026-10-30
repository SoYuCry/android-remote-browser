package io.github.soyucy.androidremote.companion;

import java.time.*;
import java.util.Random;

/** Calendar policy independent of the device's selected timezone. */
final class ScheduleWindow {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static LocalDate date(long now) { return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate(); }
    static long start(LocalDate day, int slot) { return day.atTime(slot == 0 ? 8 : 17, slot == 0 ? 35 : 50).atZone(ZONE).toInstant().toEpochMilli(); }
    static long end(LocalDate day, int slot) { return day.atTime(slot == 0 ? 8 : 18, slot == 0 ? 50 : 5).atZone(ZONE).toInstant().toEpochMilli(); }
    static int slot(long time) { return time < end(date(time), 0) ? 0 : 1; }
    static String key(long time) { return date(time) + "/" + slot(time); }
    // Old versions stored only the date, after handling the afternoon task.
    static String normalize(String handled) { return handled.length() == 10 ? handled + "/1" : handled; }
    static long end(long time) { return end(date(time), slot(time)); }
    static boolean sameWindow(long due, long now) { return key(due).equals(key(now)) && inWindow(now); }
    static boolean weekday(LocalDate day) { return WorkCalendar.working(day); }
    static boolean inWindow(long now) {
        LocalDate day = date(now);
        int slot = slot(now);
        return weekday(day) && now >= start(day, slot) && now < end(day, slot);
    }
    static long next(long now, String handledThrough, Random random) {
        LocalDate day = date(now);
        handledThrough = normalize(handledThrough);
        while (WorkCalendar.supported(day)) {
            if (weekday(day)) for (int slot = 0; slot < 2; slot++) {
                if ((day + "/" + slot).compareTo(handledThrough) <= 0) continue;
                long earliest = Math.max(start(day, slot), ((now + 999) / 1000) * 1000);
                if (earliest >= end(day, slot)) continue;
                int seconds = (int) ((end(day, slot) - earliest) / 1000);
                return earliest + random.nextInt(seconds) * 1000L;
            }
            day = day.plusDays(1);
        }
        return 0; // No published calendar: do not guess or loop across unknown years.
    }
}
