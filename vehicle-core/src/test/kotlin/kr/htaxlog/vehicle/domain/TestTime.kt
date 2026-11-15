package kr.htaxlog.vehicle.domain

import java.time.ZoneId
import java.time.ZonedDateTime

internal val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** 2026-11-02(월) 기준 KST 시각 → epoch millis. */
internal fun kst(hour: Int, minute: Int = 0, day: Int = 2): Long =
    ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, KST).toInstant().toEpochMilli()

internal const val HOUR = 60 * 60 * 1000L
internal const val MINUTE = 60 * 1000L
