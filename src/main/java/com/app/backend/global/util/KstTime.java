package com.app.backend.global.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class KstTime {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DISCORD_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private KstTime() {
    }

    // KST 오프셋(+09:00)이 붙은 OffsetDateTime
    public static OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(SEOUL).toOffsetDateTime();
    }

    // 디스코드 통지용: yyyy-MM-dd HH:mm:ss
    public static String format(LocalDateTime ldt) {
        return ldt.atZone(SEOUL).format(DISCORD_FORMAT);
    }
}