package kr.htaxlog.vehicle.session

import kr.htaxlog.vehicle.core.VehicleMotion
import kr.htaxlog.vehicle.session.DistancePolicy.Verdict
import kr.htaxlog.vehicle.session.RecoveryDecider.Decision
import kr.htaxlog.vehicle.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    private val minute = 60_000L

    // ---- DistancePolicy (F-05) ----

    @Test
    fun `거리는 계기판 차이를 10진수로 뺀다`() {
        assertEquals(Verdict.Ok(23.4), DistancePolicy.evaluate(18_420.5, 18_443.9, 0, 30 * minute))
    }

    @Test
    fun `종료 계기판을 못 읽으면 거리 미확정이다`() {
        assertTrue(DistancePolicy.evaluate(18_420.5, null, 0, 30 * minute) is Verdict.Unavailable)
        assertTrue(DistancePolicy.evaluate(null, 18_443.9, 0, 30 * minute) is Verdict.Unavailable)
    }

    @Test
    fun `계기판이 줄었으면 거리 미확정이다`() {
        assertTrue(DistancePolicy.evaluate(18_443.9, 18_420.5, 0, 30 * minute) is Verdict.Unavailable)
    }

    @Test
    fun `1500 km까지는 정상이고 넘으면 미확정이다`() {
        assertEquals(Verdict.Ok(1_500.0), DistancePolicy.evaluate(0.0, 1_500.0, 0, 20 * 60 * minute))
        assertTrue(DistancePolicy.evaluate(0.0, 1_500.1, 0, 20 * 60 * minute) is Verdict.Unavailable)
    }

    @Test
    fun `59초 동안 0점2 km면 이동 없음이다`() {
        assertEquals(Verdict.NoMovement, DistancePolicy.evaluate(100.0, 100.2, 0, 59_000))
    }

    @Test
    fun `60초를 채우거나 0점3 km를 가면 운행이다`() {
        assertEquals(Verdict.Ok(0.0), DistancePolicy.evaluate(100.0, 100.0, 0, 60_000))
        assertEquals(Verdict.Ok(0.3), DistancePolicy.evaluate(100.0, 100.3, 0, 10_000))
    }

    // ---- DrivingJudge (F-13) ----

    @Test
    fun `기어 P면 주차다`() {
        assertEquals(VehicleMotion.PARKED, DrivingJudge.judge(Gear.PARK, true, 1_000, 2_000))
        assertEquals(VehicleMotion.PARKED, DrivingJudge.judge(Gear.PARK, false, null, 2_000))
    }

    @Test
    fun `기어 D에서 10초 안에 거리가 늘었으면 주행, 아니면 정차다`() {
        assertEquals(VehicleMotion.MOVING, DrivingJudge.judge(Gear.DRIVE, true, 0, 9_999))
        assertEquals(VehicleMotion.STOPPED, DrivingJudge.judge(Gear.DRIVE, true, 0, 10_000))
        assertEquals(VehicleMotion.STOPPED, DrivingJudge.judge(Gear.DRIVE, true, null, 10_000))
    }

    @Test
    fun `차속이 3 km_h를 넘으면 계기판과 상관없이 주행이다`() {
        assertEquals(VehicleMotion.MOVING, DrivingJudge.judge(Gear.DRIVE, true, null, 60_000, speedKph = 30.0))
        assertEquals(VehicleMotion.MOVING, DrivingJudge.judge(Gear.DRIVE, false, null, 0, speedKph = 30.0))
        assertEquals(VehicleMotion.STOPPED, DrivingJudge.judge(Gear.DRIVE, true, null, 60_000, speedKph = 2.0))
    }

    @Test
    fun `기어 P면 차속이 있어도 주차다`() {
        assertEquals(VehicleMotion.PARKED, DrivingJudge.judge(Gear.PARK, true, null, 0, speedKph = 30.0))
    }

    @Test
    fun `기어 신호가 없으면 거리 불변 60초부터 주차로 본다`() {
        assertEquals(VehicleMotion.STOPPED, DrivingJudge.judge(null, true, 0, 59_999))
        assertEquals(VehicleMotion.PARKED, DrivingJudge.judge(null, true, 0, 60_000))
    }

    @Test
    fun `기어 신호 없이 시동 후 한 번도 안 움직였으면 주차다`() {
        assertEquals(VehicleMotion.PARKED, DrivingJudge.judge(null, true, null, 1_000))
    }

    @Test
    fun `기어도 계기판도 모르면 판정 불가다`() {
        assertNull(DrivingJudge.judge(null, false, null, 1_000))
        assertNull(DrivingJudge.judge(Gear.DRIVE, false, null, 1_000))
    }

    // ---- RecoveryDecider (F-10) ----

    @Test
    fun `끝나지 않은 운행의 체크포인트와 지금이 같으면 묻지 않고 끝낸다`() {
        assertEquals(Decision.AutoFinalize(18_443.9), RecoveryDecider.decide(true, 18_443.9, 18_443.9, null, null))
        assertEquals(Decision.AutoFinalize(18_443.95), RecoveryDecider.decide(true, 18_443.9, 18_443.95, null, null))
    }

    @Test
    fun `앱이 죽은 동안 더 달렸으면 복구를 묻는다`() {
        assertEquals(Decision.AskRecovery(5.0), RecoveryDecider.decide(true, 18_443.9, 18_448.9, null, null))
    }

    @Test
    fun `계기판을 못 읽으면 끝나지 않은 운행을 이어서 기록한다`() {
        assertEquals(Decision.ContinueRecording, RecoveryDecider.decide(true, 18_443.9, null, null, null))
        assertEquals(Decision.Nothing, RecoveryDecider.decide(false, null, null, 100.0, null))
    }

    @Test
    fun `기록 없이 15 km 늘었으면 놓친 운행이다`() {
        assertEquals(Decision.MissedTrip(15.0), RecoveryDecider.decide(false, null, 18_458.9, 18_443.9, null))
    }

    @Test
    fun `1 km 미만이거나 무시한 값이면 묻지 않는다`() {
        assertEquals(Decision.Nothing, RecoveryDecider.decide(false, null, 18_444.8, 18_443.9, null))
        assertEquals(Decision.Nothing, RecoveryDecider.decide(false, null, 18_458.9, 18_443.9, 18_458.9))
        assertEquals(Decision.Nothing, RecoveryDecider.decide(false, null, 18_458.9, null, null))
    }
}
