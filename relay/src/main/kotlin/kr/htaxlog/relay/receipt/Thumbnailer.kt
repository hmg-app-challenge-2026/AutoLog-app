package kr.htaxlog.relay.receipt

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 영수증 썸네일 (기술명세 4.5 단계 3). 차 화면(F-19)에 보여 준다.
 *
 * - 폰 사진의 EXIF 회전값을 반영해, 찍을 때 본 방향 그대로 세운 뒤 긴 변 1600px JPEG로 줄인다.
 *   (JPEG를 다시 쓰면 EXIF가 사라지므로 회전을 픽셀에 적용해야 한다.)
 * - 카드번호 영역은 가리지 않는다. 국내 카드 영수증은 번호 일부가 이미 가려져 인쇄되고,
 *   차로 가는 OCR 값에는 끝 4자리만 남긴다([ReceiptVerifier.mask]).
 * - ImageIO가 못 읽는 형식(HEIC 등)이면 null → 원본을 썸네일로 쓴다.
 */
object Thumbnailer {
    const val MAX_EDGE_PX = 1600
    private const val QUALITY = 0.9f

    fun jpeg(image: ByteArray): ByteArray? {
        val src = runCatching { ImageIO.read(ByteArrayInputStream(image)) }.getOrNull() ?: return null
        return encode(applyOrientation(src, orientation(image)), MAX_EDGE_PX)
    }

    private fun encode(img: BufferedImage, maxEdgePx: Int): ByteArray {
        val scale = min(1.0, maxEdgePx.toDouble() / max(img.width, img.height))
        val w = max(1, (img.width * scale).roundToInt())
        val h = max(1, (img.height * scale).roundToInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(img, 0, 0, w, h, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val bytes = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bytes).use { ios ->
            writer.output = ios
            val param =
                writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
            writer.write(null, IIOImage(out, null, null), param)
            writer.dispose()
        }
        return bytes.toByteArray()
    }

    /** EXIF Orientation(1~8). 없거나 못 읽으면 1(그대로). */
    internal fun orientation(image: ByteArray): Int = runCatching {
        ImageMetadataReader
            .readMetadata(ByteArrayInputStream(image))
            .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.takeIf { it.containsTag(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.getInt(ExifIFD0Directory.TAG_ORIENTATION)
    }.getOrNull() ?: 1

    /** EXIF Orientation 값대로 돌리거나 뒤집는다. 5~8은 가로세로가 바뀐다. */
    internal fun applyOrientation(src: BufferedImage, orientation: Int): BufferedImage {
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val t =
            when (orientation) {
                2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0) // 좌우 반전
                3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h) // 180°
                4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h) // 상하 반전
                5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0) // 전치
                6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0) // 시계 90°
                7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w) // 반대 전치
                8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w) // 반시계 90°
                else -> return src
            }
        val swap = orientation >= 5
        val out =
            BufferedImage(
                if (swap) src.height else src.width,
                if (swap) src.width else src.height,
                BufferedImage.TYPE_INT_RGB,
            )
        out.createGraphics().apply {
            drawImage(src, t, null)
            dispose()
        }
        return out
    }
}
