package kr.htaxlog.relay.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트에서 시간을 앞으로 돌릴 수 있는 시계. */
class TestClock(private var now: Instant = Instant.parse("2026-11-08T00:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advance(d: Duration) {
        now = now.plus(d)
    }
}
