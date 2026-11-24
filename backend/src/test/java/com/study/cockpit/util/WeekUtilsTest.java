package com.study.cockpit.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UTC+8 周次归属单元测试：周一为一周起始，周日为结算节点。
 */
class WeekUtilsTest {

    @Test
    void mondayOf_shouldReturnMondayForAnyWeekday() {
        // 2026-11-24 是周二，所在周周一为 11-23
        LocalDate tuesday = LocalDate.of(2026, 11, 24);
        assertEquals(LocalDate.of(2026, 11, 23), WeekUtils.mondayOf(tuesday));
    }

    @Test
    void sundayOf_shouldReturnSundayOfSameWeek() {
        LocalDate tuesday = LocalDate.of(2026, 11, 24);
        assertEquals(LocalDate.of(2026, 11, 29), WeekUtils.sundayOf(tuesday));
    }

    @Test
    void weekDays_shouldReturnSevenConsecutiveDaysFromMonday() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        List<LocalDate> days = WeekUtils.weekDays(monday);
        assertEquals(7, days.size());
        assertEquals(DayOfWeek.MONDAY, days.get(0).getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, days.get(6).getDayOfWeek());
        assertEquals(monday, days.get(0));
        assertEquals(monday.plusDays(6), days.get(6));
    }

    @Test
    void parseWeekStart_shouldAcceptValidMonday() {
        LocalDate date = WeekUtils.parseWeekStart("2026-11-23");
        assertEquals(DayOfWeek.MONDAY, date.getDayOfWeek());
    }

    @Test
    void parseWeekStart_shouldRejectNonMonday() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WeekUtils.parseWeekStart("2026-11-24"));
        assertTrue(ex.getMessage().contains("周一"));
    }

    @Test
    void parseWeekStart_shouldRejectMalformedFormat() {
        assertThrows(IllegalArgumentException.class, () -> WeekUtils.parseWeekStart("2026/11/23"));
    }

    @Test
    void parseWeekStart_shouldRejectImpossibleDate() {
        assertThrows(IllegalArgumentException.class, () -> WeekUtils.parseWeekStart("2026-02-30"));
    }
}
