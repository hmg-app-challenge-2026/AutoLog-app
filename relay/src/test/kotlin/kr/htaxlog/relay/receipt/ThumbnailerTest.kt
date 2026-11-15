package kr.htaxlog.relay.receipt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ThumbnailerTest {
    /** 가로 40 x 세로 20, 왼쪽 위 8x8만 빨강 (JPEG 압축에도 색이 남도록 블록 크기). */
    private fun marked(): BufferedImage = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, 40, 20)
            color = Color.RED
            fillRect(0, 0, 8, 8)
            dispose()
        }
    }

    private fun isRed(rgb: Int) = Color(rgb).let { it.red > 200 && it.green < 60 && it.blue < 60 }

    private fun jpegBytes(img: BufferedImage) = ByteArrayOutputStream().also {
        ImageIO.write(img, "jpg", it)
    }.toByteArray()

    /** JPEG의 SOI 바로 뒤에 Orientation 태그 하나짜리 EXIF(APP1) 세그먼트를 넣는다. */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff =
            byteArrayOf(
                0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // MM, 42, IFD0 offset 8
                0x00, 0x01, // 항목 1개
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, // Orientation, SHORT, count 1
                0x00, orientation.toByte(), 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, // 다음 IFD 없음
            )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val len = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun read(bytes: ByteArray) = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun `EXIF Orientation 6이면 시계 방향 90도로 세운 썸네일을 만든다`() {
        val bytes = withExifOrientation(jpegBytes(marked()), 6)

        val thumb = read(Thumbnailer.jpeg(bytes)!!)

        assertEquals(6, Thumbnailer.orientation(bytes))
        assertEquals(20, thumb.width)
        assertEquals(40, thumb.height)
        assertTrue(isRed(thumb.getRGB(16, 3)), "왼쪽 위 → 오른쪽 위")
    }

    @Test
    fun `EXIF가 없으면 방향을 그대로 둔다`() {
        val bytes = jpegBytes(marked())

        val thumb = read(Thumbnailer.jpeg(bytes)!!)

        assertEquals(1, Thumbnailer.orientation(bytes))
        assertEquals(40, thumb.width)
        assertTrue(isRed(thumb.getRGB(3, 3)))
    }

    @Test
    fun `Orientation 값마다 빨간 점이 가야 할 모서리로 간다`() {
        // 결과 이미지에서 원래 왼쪽 위 픽셀이 있어야 할 자리
        val expected =
            mapOf(
                2 to (39 to 0),
                3 to (39 to 19),
                4 to (0 to 19),
                5 to (0 to 0),
                6 to (19 to 0),
                7 to (19 to 39),
                8 to (0 to 39),
            )
        expected.forEach { (o, xy) ->
            val out = Thumbnailer.applyOrientation(marked(), o)
            assertTrue(isRed(out.getRGB(xy.first, xy.second)), "orientation=$o")
        }
    }

    @Test
    fun `썸네일은 긴 변 1600px로 줄인다`() {
        val big = jpegBytes(BufferedImage(3200, 1000, BufferedImage.TYPE_INT_RGB))

        val thumb = read(Thumbnailer.jpeg(big)!!)

        assertEquals(1600, thumb.width)
        assertEquals(500, thumb.height)
    }

    @Test
    fun `읽을 수 없는 이미지는 null`() {
        assertNull(Thumbnailer.jpeg("not-an-image".toByteArray()))
    }
}
