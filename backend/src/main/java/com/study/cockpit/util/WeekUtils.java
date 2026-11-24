package com.study.cockpit.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * UTC+8 时间/周工具（需求 4：每周周一起始、周日结算，所有时间统一 UTC+8）。
 * 全部基于 Asia/Shanghai，不依赖服务器默认时区，避免周次计算错乱。
 */
public final class WeekUtils {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter STRICT_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private WeekUtils() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** 所在周的周一 */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 所在周的周日 */
    public static LocalDate sundayOf(LocalDate date) {
        return mondayOf(date).plusDays(6);
    }

    public static List<LocalDate> weekDays(LocalDate monday) {
        List<LocalDate> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            days.add(monday.plusDays(i));
        }
        return days;
    }

    /** 严格解析周一日期，非法格式直接拒绝，防止拼接进文件名/SQL 造成注入 */
    public static LocalDate parseWeekStart(String text) {
        try {
            LocalDate date = LocalDate.parse(text, STRICT_DATE);
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                throw new IllegalArgumentException("weekStart 必须是周一");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("weekStart 格式必须为 yyyy-MM-dd");
        }
    }

    public static String format(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
