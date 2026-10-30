package io.github.soyucy.androidremote.companion;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Mainland China's published national schedule; company-specific leave is not included. */
final class WorkCalendar {
    // State Council, 2025-11-04:
    // https://www.gov.cn/zhengce/content/202511/content_7047090.htm
    private static final Set<String> MAKEUP = new HashSet<>(Arrays.asList(
            "2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10"));
    private static final String[][] HOLIDAYS = {
            {"2026-01-01", "2026-01-03"}, {"2026-02-15", "2026-02-23"},
            {"2026-04-04", "2026-04-06"}, {"2026-05-01", "2026-05-05"},
            {"2026-06-19", "2026-06-21"}, {"2026-09-25", "2026-09-27"},
            {"2026-10-01", "2026-10-07"}
    };
    static boolean supported(LocalDate day) { return day.getYear() == 2026; }
    static boolean working(LocalDate day) {
        if (!supported(day)) return false;
        String date = day.toString();
        if (MAKEUP.contains(date)) return true;
        for (String[] holiday : HOLIDAYS) {
            if (date.compareTo(holiday[0]) >= 0 && date.compareTo(holiday[1]) <= 0) return false;
        }
        return day.getDayOfWeek().getValue() <= 5;
    }
}
