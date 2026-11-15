package kr.htaxlog.vehicle.vehicle

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pleos Connect Virtual Console(에뮬레이터 안 8088 포트) HTTP 클라이언트. 탐침 테스트가 차량 값을 바꾸는 데 쓴다.
 * 콘솔은 브라우저 세션 하트비트(`POST /browser/session`, 3초 타임아웃)가 살아 있을 때만 값을 받는다.
 * 맥에서 같은 콘솔을 보려면: `adb forward tcp:8088 tcp:8088` → 브라우저로 http://localhost:8088
 */
class VirtualConsole(private val baseUrl: String = "http://127.0.0.1:8088") {
    private var heartbeat: Job? = null

    /** 하트비트를 1초마다 보낸다. 테스트가 끝나면 [stop]. */
    fun start(scope: CoroutineScope) {
        heartbeat = scope.launch(Dispatchers.IO) {
            while (isActive) {
                request("POST", "/browser/session?client=htaxlog-probe")
                delay(1_000)
            }
        }
    }

    fun stop() {
        heartbeat?.cancel()
        request("POST", "/browser/session?client=htaxlog-probe&closing=1")
    }

    fun setOdometerKm(km: Double) = request("PUT", "/vehicle/odometer?traveled_distance_km=$km")

    /** [gear]: Park / Reverse / Neutral / Drive. */
    fun setGear(gear: String) = request("PUT", "/vehicle/gear?gear=$gear")

    fun put(path: String) = request("PUT", path)

    private fun request(method: String, path: String): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 3_000
            conn.readTimeout = 3_000
            val body = (if (conn.responseCode < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode >= 400 || body.contains("\"error\"")) Log.w(TAG, "$method $path → $body")
            body
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "HTaxLog/probe"
    }
}

/** 전원 상태 시뮬레이터: Off / Ready / On / Driving 등. */
fun VirtualConsole.setPower(state: String) = put("/vehicle/power-system/simulator?state=$state")
