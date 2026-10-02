package ru.prorabprime.server.storage

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReceiptReaderTest {

    private val reader = ZxingReceiptReader(Dispatchers.Default)

    private fun qrPng(text: String, size: Int = 300): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArrayOutputStream().also { MatrixToImageWriter.writeToStream(matrix, "png", it) }.toByteArray()
    }

    @Test
    fun `the sum and the time are read from a fiscal code`() {
        val data = parseFiscalQr("t=20261001T1526&s=790.00&fn=9960440300123456&i=12345&fp=1234567890&n=1")

        assertThat(data?.amountKopecks).isEqualTo(79_000L)
        assertThat(data?.purchasedAt).isEqualTo("2026-10-01T15:26")
    }

    @Test
    fun `the other spellings of the time and the sum are read too`() {
        assertThat(parseFiscalQr("t=20261001T152659&s=1250.5&n=1")?.purchasedAt).isEqualTo("2026-10-01T15:26")
        assertThat(parseFiscalQr("t=2026-10-01T15:26:00&s=1250,5")?.amountKopecks).isEqualTo(125_050L)
        assertThat(parseFiscalQr("s=300")?.amountKopecks).isEqualTo(30_000L)
        assertThat(parseFiscalQr("s=300")?.purchasedAt).isNull()
    }

    @Test
    fun `a text that is not a fiscal code, or has no usable sum, is nothing`() {
        assertThat(parseFiscalQr("https://example.com/menu")).isNull()
        assertThat(parseFiscalQr("t=20261001T1526&n=1")).isNull()
        assertThat(parseFiscalQr("t=20261001T1526&s=-5.00")).isNull()
        assertThat(parseFiscalQr("t=20261001T1526&s=1.234")).isNull()
        assertThat(parseFiscalQr("s=abc")).isNull()
    }

    @Test
    fun `a photographed code is found and read`() = runTest {
        val data = reader.read(qrPng("t=20261001T1526&s=790.00&fn=1&i=2&fp=3&n=1"))

        assertThat(data?.amountKopecks).isEqualTo(79_000L)
        assertThat(data?.purchasedAt).isEqualTo("2026-10-01T15:26")
        assertThat(data?.qr).startsWith("t=20261001T1526")
    }

    @Test
    fun `a code that is small in the picture is found`() = runTest {
        // A receipt's code is a few percent of a 2000 px photograph: draw one small on a large white sheet.
        val code = ImageIO.read(qrPng("t=20261001T1526&s=1520.40&n=1", size = 160).inputStream())
        val sheet = java.awt.image.BufferedImage(1600, 1200, java.awt.image.BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                color = java.awt.Color.WHITE
                fillRect(0, 0, 1600, 1200)
                drawImage(code, 1200, 800, null)
                dispose()
            }
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(sheet, "png", it) }.toByteArray()

        assertThat(reader.read(bytes)?.amountKopecks).isEqualTo(152_040L)
    }

    @Test
    fun `a code of something else, a picture without a code and a non-image give nothing`() = runTest {
        assertThat(reader.read(qrPng("https://example.com"))).isNull()
        assertThat(reader.read(TestImages.jpeg(200, 100))).isNull()
        assertThat(reader.read(byteArrayOf(1, 2, 3))).isNull()
    }
}
