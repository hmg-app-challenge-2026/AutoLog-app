package kr.autolog.vehicle.ui.design.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kr.autolog.vehicle.R
import kr.autolog.vehicle.ui.design.Radius

/** QR 매트릭스(모듈 단위, 여백 4모듈 포함). 폰 카메라가 차 화면 반사 속에서도 읽게 오류 정정 M. */
fun qrMatrix(content: String): BitMatrix = QRCodeWriter().encode(
    content,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bmp.setPixel(
                x,
                y,
                if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bmp.asImageBitmap()
}

/** QR은 테마와 상관없이 흰 바탕·검은 모듈(카메라 인식). 확대는 FilterQuality.None으로 모듈 경계를 선명하게. */
@Composable
fun QrImage(content: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(content) { qrMatrix(content).toImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.qr_description),
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size).clip(RoundedCornerShape(Radius.sm)).background(Color.White),
    )
}
