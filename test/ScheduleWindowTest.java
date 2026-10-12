package io.github.soyucy.androidremote.companion;

import java.time.*;
import java.util.Random;

public class ScheduleWindowTest {
    static long at(String day, int h, int m, int s) {
        return LocalDate.parse(day).atTime(h,m,s).atZone(ScheduleWindow.ZONE).toInstant().toEpochMilli();
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        Random r = new Random(42);
        for (int i=0;i<10000;i++) {
            long next = ScheduleWindow.next(at("2026-10-12",9,0,0), "", r);
            check(next >= at("2026-10-12",17,45,0) && next < at("2026-10-12",18,5,0));
            check(ScheduleWindow.inWindow(next));
        }
        check(!ScheduleWindow.inWindow(at("2026-10-12",17,44,59)));
        check(!ScheduleWindow.inWindow(at("2026-10-12",18,5,0)));
        check(!ScheduleWindow.inWindow(at("2026-10-17",17,50,0)));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-16",18,5,0),"",r)).equals(LocalDate.parse("2026-10-19")));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",17,50,0),"2026-10-12",r)).equals(LocalDate.parse("2026-10-13")));
        check(ScheduleWindow.next(at("2026-10-12",18,4,59),"",r) == at("2026-10-12",18,4,59));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",18,4,59)+999,"",r)).equals(LocalDate.parse("2026-10-13")));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",9,0,0),"2026-10-16",r)).equals(LocalDate.parse("2026-10-19")));
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
        check(ScheduleWindow.inWindow(at("2026-10-12",17,50,0)));
        System.out.println("PASS: weekday selection, random bounds, partial window, weekend, duplicate-day exclusion, clock rollback, timezone independence");
    }
}
