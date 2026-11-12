package kr.autolog.vehicle.common

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 날짜를 자르는 기준 시간대(월 요약·운행기록부·이력의 달). 국내 서비스라 한국 시간 하나. */
val AppZone: ZoneId = ZoneId.of("Asia/Seoul")

/** [AppZone] 기준 지금 달. */
fun AppClock.currentMonth(): YearMonth = YearMonth.from(Instant.ofEpochMilli(nowMs()).atZone(AppZone))
