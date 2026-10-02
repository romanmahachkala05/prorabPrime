package ru.prorabprime.server.storage

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.prorabprime.server.model.ReceiptData

/** Reads the fiscal QR code off a photo of a receipt. */
interface ReceiptReader {
    /** Null when there is no readable code, or it is not a receipt's; never fails the caller. */
    suspend fun read(bytes: ByteArray): ReceiptData?
}

class ZxingReceiptReader(
    private val dispatcher: CoroutineDispatcher,
) : ReceiptReader {

    override suspend fun read(bytes: ByteArray): ReceiptData? = withContext(dispatcher) {
        runCatching { decode(bytes)?.let(::parseFiscalQr) }.getOrNull()
    }

    private fun decode(bytes: ByteArray): String? {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val source: LuminanceSource = BufferedImageLuminanceSource(image)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        // A receipt is photographed unevenly lit: the two ways of telling black from white fail on different shots.
        return listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source)).firstNotNullOfOrNull { binarizer ->
            try {
                MultiFormatReader().decode(BinaryBitmap(binarizer), hints).text
            } catch (_: NotFoundException) {
                null
            }
        }
    }
}

private val QR_TIME = Regex("""^(\d{4})-?(\d{2})-?(\d{2})T(\d{2}):?(\d{2})(?::?\d{2})?$""")

/**
 * The text of a fiscal receipt code: `t=20261001T1526&s=790.00&fn=…&i=…&fp=…&n=1`. Only the sum is
 * required; anything that is not such a code, or has no sum, is null.
 */
internal fun parseFiscalQr(text: String): ReceiptData? {
    val fields = text.trim().split('&').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() }
    }.toMap()
    val amount = fields["s"]?.let(::kopecksOf) ?: return null
    val purchasedAt = fields["t"]?.let { QR_TIME.matchEntire(it) }?.groupValues?.let { g ->
        "${g[1]}-${g[2]}-${g[3]}T${g[4]}:${g[5]}"
    }
    return ReceiptData(amount, purchasedAt, text.trim())
}

/** `790.00`, `790,5` and `790` as kopecks; null for anything else, including a negative. */
private fun kopecksOf(text: String): Long? {
    val parts = text.replace(',', '.').split('.')
    val rubles = parts[0].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull() ?: return null
    val fraction = parts.getOrNull(1).orEmpty()
    val valid = parts.size <= 2 && fraction.length <= 2 && fraction.all(Char::isDigit)
    return if (valid) rubles * KOPECKS_PER_RUBLE + fraction.padEnd(2, '0').toLong() else null
}

private const val KOPECKS_PER_RUBLE = 100L
