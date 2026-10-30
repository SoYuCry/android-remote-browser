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
        String[] makeup = {"2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10"};
        for (String day : makeup) {
            check(WorkCalendar.working(LocalDate.parse(day)));
            check(ScheduleWindow.inWindow(at(day,8,40,0)));
            check(ScheduleWindow.inWindow(at(day,17,55,0)));
            check(ScheduleWindow.date(ScheduleWindow.next(at(day,0,0,0),"",r)).toString().equals(day));
        }
        String[][] holidays = {{"2026-01-01", "2026-01-03"}, {"2026-02-15", "2026-02-23"},
            {"2026-04-04", "2026-04-06"}, {"2026-05-01", "2026-05-05"},
            {"2026-06-19", "2026-06-21"}, {"2026-09-25", "2026-09-27"}, {"2026-10-01", "2026-10-07"}};
        for (String[] range : holidays) {
            for (LocalDate day = LocalDate.parse(range[0]); !day.isAfter(LocalDate.parse(range[1])); day = day.plusDays(1)) {
                check(!WorkCalendar.working(day));
                check(!ScheduleWindow.inWindow(at(day.toString(),8,40,0)));
                check(!ScheduleWindow.inWindow(at(day.toString(),17,55,0)));
            }
        }
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-01-01",0,0,0),"",r)).equals("2026-01-04/0"));
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-10-09",18,5,0),"",r)).equals("2026-10-10/0"));
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-10-10",8,40,0),"2026-10-10/0",r)).equals("2026-10-10/1"));
        check(ScheduleWindow.next(at("2026-12-31",18,5,0),"",r) == 0);
        check(ScheduleWindow.next(at("2027-01-04",8,0,0),"",r) == 0);
        check(!ScheduleWindow.inWindow(at("2027-01-04",8,40,0)));
        check(ScheduleWindow.next(at("2026-10-12",8,0,0),"2027-01-04/1",r) == 0);
        for (int i=0;i<10000;i++) {
            long morning = ScheduleWindow.next(at("2026-10-12",8,0,0), "", r);
            check(morning >= at("2026-10-12",8,35,0) && morning < at("2026-10-12",8,50,0));
            check(ScheduleWindow.inWindow(morning));
            long afternoon = ScheduleWindow.next(morning, ScheduleWindow.key(morning), r);
            check(afternoon >= at("2026-10-12",17,50,0) && afternoon < at("2026-10-12",18,5,0));
            long next = ScheduleWindow.next(at("2026-10-12",9,0,0), "", r);
            check(next >= at("2026-10-12",17,50,0) && next < at("2026-10-12",18,5,0));
            check(ScheduleWindow.inWindow(next));
        }
        check(!ScheduleWindow.inWindow(at("2026-10-12",17,49,59)));
        check(!ScheduleWindow.inWindow(at("2026-10-12",8,34,59)));
        check(!ScheduleWindow.inWindow(at("2026-10-12",8,50,0)));
        check(!ScheduleWindow.inWindow(at("2026-10-17",8,40,0)));
        check(!ScheduleWindow.sameWindow(at("2026-10-12",8,40,0), at("2026-10-12",17,50,0)));
        check(ScheduleWindow.next(at("2026-10-12",8,49,59),"",r) == at("2026-10-12",8,49,59));
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-10-12",8,49,59)+999,"",r)).equals("2026-10-12/1"));
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-10-12",8,0,0),"2026-10-12/0",r)).equals("2026-10-12/1"));
        check(ScheduleWindow.key(ScheduleWindow.next(at("2026-10-12",8,0,0),"2026-10-12/1",r)).equals("2026-10-13/0"));
        check(!ScheduleWindow.inWindow(at("2026-10-12",18,5,0)));
        check(!ScheduleWindow.inWindow(at("2026-10-17",17,50,0)));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-16",18,5,0),"",r)).equals(LocalDate.parse("2026-10-19")));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",17,50,0),"2026-10-12",r)).equals(LocalDate.parse("2026-10-13")));
        check(ScheduleWindow.next(at("2026-10-12",18,4,59),"",r) == at("2026-10-12",18,4,59));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",18,4,59)+999,"",r)).equals(LocalDate.parse("2026-10-13")));
        check(ScheduleWindow.date(ScheduleWindow.next(at("2026-10-12",9,0,0),"2026-10-16",r)).equals(LocalDate.parse("2026-10-19")));
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
        check(ScheduleWindow.inWindow(at("2026-10-12",17,50,0)));
        System.out.println("PASS: official holidays, makeup days, unknown calendar, random bounds, partial window, per-window exclusion, clock rollback, timezone independence");
    }
}
