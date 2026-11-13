package kr.autolog.vehicle.session

import kr.autolog.shared.PlaceKind
import kr.autolog.vehicle.core.AddressSource
import kr.autolog.vehicle.core.GeocodeResult
import kr.autolog.vehicle.core.PlaceInfo
import kr.autolog.vehicle.data.PlaceSource
import kr.autolog.vehicle.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacePolicyTest {
    private val trip = TripEntity(tripId = "t", vehicleId = "v", userId = null, startTime = 0L, startOdometerKm = 1.0)
    private val kakao = GeocodeResult("서울 중구 태평로1가 세종대로 110", "서울시청", AddressSource.KAKAO_RELAY)
    private val navi = GeocodeResult("서울 중구 세종대로 110", null, AddressSource.NAVI_HELPER)
    private val office = PlaceInfo("p", "본사", PlaceKind.OFFICE, 37.5663, 126.9779)

    @Test
    fun `출발지 이름은 등록 장소가 건물 이름보다 먼저다`() {
        assertEquals("본사", PlacePolicy.mergeOrigin(trip, office, kakao).originLabel)
        assertEquals("서울시청", PlacePolicy.mergeOrigin(trip, null, kakao).originLabel)
    }

    @Test
    fun `카카오 주소를 내비 주소로 되돌리지 않는다`() {
        val withKakao = trip.copy(originAddress = kakao.address, originAddressSource = AddressSource.KAKAO_RELAY)

        val m = PlacePolicy.mergeOrigin(withKakao, null, navi)

        assertEquals(kakao.address, m.originAddress)
        assertEquals(AddressSource.KAKAO_RELAY, m.originAddressSource)
    }

    @Test
    fun `주소를 못 얻으면 지금 값을 그대로 둔다`() {
        val withNavi = trip.copy(originAddress = "내비 주소", originAddressSource = AddressSource.NAVI_HELPER)

        assertEquals(withNavi, PlacePolicy.mergeOrigin(withNavi, null, null))
    }

    @Test
    fun `내비 목적지 300 m 안에서 끝나면 목적지 이름을 남긴다`() {
        val toNavi = trip.copy(
            destinationLabel = "판교 거래처",
            destinationLat = 37.401,
            destinationLng = 127.108,
            destinationSource = PlaceSource.NAVI_HELPER,
        )
        // 위도 0.002도 ≈ 222 m
        val end = EndPosition(37.403, 127.108, PlaceSource.NAVI_CURRENT)

        val m = PlacePolicy.mergeDestination(toNavi, end, null, kakao)

        assertEquals("판교 거래처", m.destinationLabel)
        assertEquals(37.403, m.destinationLat!!, 0.0)
        assertEquals(PlaceSource.NAVI_CURRENT, m.destinationSource)
        assertEquals(kakao.address, m.destinationAddress)
    }

    @Test
    fun `내비 목적지와 멀면 목적지 이름과 내비 주소를 버린다`() {
        val toNavi = trip.copy(
            destinationLabel = "판교 거래처",
            destinationLat = 37.401,
            destinationLng = 127.108,
            destinationSource = PlaceSource.NAVI_HELPER,
            destinationAddress = "경기 성남시 분당구 판교역로 1",
            destinationAddressSource = AddressSource.NAVI_HELPER,
        )
        // 위도 0.004도 ≈ 445 m
        val end = EndPosition(37.405, 127.108, PlaceSource.FUSED_LOCATION)

        val m = PlacePolicy.mergeDestination(toNavi, end, null, null)

        assertNull(m.destinationLabel)
        assertNull(m.destinationAddress)
        assertEquals(37.405, m.destinationLat!!, 0.0)
    }

    @Test
    fun `실제 위치를 모르면 내비 목적지를 믿는다`() {
        val toNavi = trip.copy(
            destinationLabel = "판교 거래처",
            destinationLat = 37.401,
            destinationLng = 127.108,
            destinationSource = PlaceSource.NAVI_HELPER,
        )

        val m = PlacePolicy.mergeDestination(toNavi, null, null, kakao)

        assertEquals("판교 거래처", m.destinationLabel)
        assertEquals(37.401, m.destinationLat!!, 0.0)
        assertEquals(kakao.address, m.destinationAddress)
    }

    @Test
    fun `사람이 고른 도착지는 이름과 좌표를 그대로 두고 주소만 붙인다`() {
        val chosen = trip.copy(destinationLabel = "판교 A고객사", destinationSource = PlaceSource.USER_SELECTED)
        val end = EndPosition(37.5, 127.0, PlaceSource.NAVI_CURRENT)

        val m = PlacePolicy.mergeDestination(chosen, end, office, kakao)

        assertEquals("판교 A고객사", m.destinationLabel)
        assertNull(m.destinationLat)
        assertEquals(kakao.address, m.destinationAddress)
    }
}
