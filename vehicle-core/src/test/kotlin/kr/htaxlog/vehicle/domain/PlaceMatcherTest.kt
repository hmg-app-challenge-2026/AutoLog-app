package kr.htaxlog.vehicle.domain

import kr.htaxlog.shared.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceMatcherTest {
    private val client = Place("p-client", "판교 A고객사", PlaceKind.CLIENT, 37.4000, 127.1000, radiusM = 150)
    private val gas = Place("p-gas", "주유소", PlaceKind.GAS_STATION, 37.4015, 127.1000, radiusM = 150)

    @Test
    fun `반경 안이면 그 장소로 매칭한다`() {
        // 위도 0.001도 ≈ 111 m
        assertEquals(client, PlaceMatcher.match(37.4010, 127.1000, listOf(client)))
    }

    @Test
    fun `반경 150m에 여유 50m를 더한 거리까지 매칭한다`() {
        // ≈ 189 m
        assertEquals(client, PlaceMatcher.match(37.4017, 127.1000, listOf(client)))
    }

    @Test
    fun `반경과 여유를 넘으면 매칭하지 않는다`() {
        // ≈ 222 m
        assertNull(PlaceMatcher.match(37.4020, 127.1000, listOf(client)))
    }

    @Test
    fun `여러 곳이 겹치면 가장 가까운 곳`() {
        assertEquals(gas, PlaceMatcher.match(37.4012, 127.1000, listOf(client, gas)))
    }

    @Test
    fun `강남역에서 판교역까지 직선거리는 약 13점6km다`() {
        // 강남역 → 판교역 직선 약 13.6 km (±1 km)
        val d = PlaceMatcher.distanceM(37.4979, 127.0276, 37.3948, 127.1112)

        assertEquals(13_600.0, d, 1_000.0)
    }
}
