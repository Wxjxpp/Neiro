package com.wxjxpp.neiro.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 10 段图形均衡器（Expr）。
 *
 * 频段对齐 Poweramp 图形 EQ：31/62/124/249/498/996/1995/3993/7993/16000 Hz。
 * 每段一个 peaking biquad，时域 Direct Form I 逐样本滤波——与既有 8-bit/Turbo
 * 处理器同样走 DefaultAudioSink 的音频处理链，零额外权限、零延迟开关。
 * 仅处理 16-bit PCM；其他编码原样透传。
 *
 * 线程模型：UI 线程调用 [setBandGains]/[setPreamplification]，内部仅写字段；
 * 音频线程逐样本读取这些字段（float 读写在 JVM 上原子性足够，无需锁）。
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        /** Poweramp 图形 EQ 的标准中心频率。 */
        val BAND_FREQS = floatArrayOf(31f, 62f, 124f, 249f, 498f, 996f, 1995f, 3993f, 7993f, 16000f)
        const val MAX_GAIN_DB = 12f
        /** 内置预设：学习自 Poweramp 原版图形均衡器（10-band），增益四舍五入到 0.1dB。 */
        val PRESETS: Map<String, FloatArray> = mapOf(
            "平直" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "低音" to floatArrayOf(5.8f, 5.8f, 3.0f, 0.0f, -1.5f, -1.5f, 0.0f, 0.0f, 0.0f, 0.0f),
            "强劲低音" to floatArrayOf(8f, 7f, 4.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
            "高音" to floatArrayOf(-3.0f, -3.0f, -3.0f, -3.0f, -1.5f, -1.5f, 0.0f, 6.3f, 9.6f, 12.3f),
            "中音" to floatArrayOf(-4.5f, -4.5f, -1.5f, 1.5f, 4.5f, 4.5f, 1.5f, 0.0f, -4.5f, -6.0f),
            "人声" to floatArrayOf(-3.0f, -1.5f, 0.0f, 3.0f, 5.5f, 5.5f, 3.0f, 1.5f, 0.0f, -1.5f),
            "摇滚" to floatArrayOf(5.8f, 3.2f, 1.3f, -3.0f, -2.3f, 2.3f, 3.6f, 5.8f, 5.8f, 5.8f),
            "流行" to floatArrayOf(1.5f, 4.5f, 5.8f, 3.0f, 1.5f, 0.0f, 0.0f, 0.0f, 1.5f, 3.0f),
            "舞曲" to floatArrayOf(5.8f, 3.1f, 2.1f, 0.0f, 0.0f, -2.7f, -2.0f, -2.2f, -0.6f, 0.1f),
            "电子" to floatArrayOf(5.8f, 5.8f, 0.0f, -2.8f, -2.1f, 0.0f, 3.6f, 7.4f, 7.7f, 7.5f),
            "经典" to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -3.0f, -3.0f, -3.0f, -4.5f),
            "现场" to floatArrayOf(-4.5f, -3.8f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.5f, 1.5f, 1.5f),
        )
    }

    private var enabled = false
    private var preampDb = 0f

    /** 各段增益 dB，索引对应 [BAND_FREQS]。 */
    private val bandGains = FloatArray(10)
    /** 各段 biquad 系数（Direct Form I），按采样率重算。 */
    private val coeffs = Array(10) { DoubleArray(5) }
    /** 各段状态 [x1,x2,y1,y2] × 声道。 */
    private var state: Array<FloatArray> = emptyArray()
    private var sampleRate = 0
    private var channelCount = 0

    fun setEnabledMode(on: Boolean) {
        if (enabled == on) return
        enabled = on
        runCatching { flush() }
    }

    fun setBandGains(gainsDb: FloatArray) {
        require(gainsDb.size == 10) { "需要 10 个频段增益" }
        for (i in 0 until 10) bandGains[i] = gainsDb[i].coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
        if (sampleRate > 0) recalcCoeffs()
    }

    fun setPreamplification(db: Float) {
        preampDb = db.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        if (enabled && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && sampleRate > 0) {
            // 超出奈奎斯特的频段自动禁用该段系数（直通）
            recalcCoeffs()
        }
        return inputAudioFormat
    }

    private fun recalcCoeffs() {
        val nyquist = sampleRate / 2f
        for (i in 0 until 10) {
            val f0 = BAND_FREQS[i]
            if (f0 >= nyquist * 0.95f || bandGains[i] == 0f) {
                // 直通段：b0=1 其余 0
                coeffs[i][0] = 1.0; coeffs[i][1] = 0.0; coeffs[i][2] = 0.0
                coeffs[i][3] = 0.0; coeffs[i][4] = 0.0
                continue
            }
            val A = 10.0.pow(bandGains[i] / 40.0)
            val w0 = 2.0 * PI * f0 / sampleRate
            val cw = cos(w0)
            val sw = sin(w0)
            val Q = 1.1 // 略宽于谐振，接近 Poweramp 图形 EQ 手感
            val alpha = sw / (2.0 * Q)
            val b0 = 1.0 + alpha * A
            val b1 = -2.0 * cw
            val b2 = 1.0 - alpha * A
            val a0 = 1.0 + alpha / A
            val a1 = -2.0 * cw
            val a2 = 1.0 - alpha / A
            coeffs[i][0] = b0 / a0; coeffs[i][1] = b1 / a0; coeffs[i][2] = b2 / a0
            coeffs[i][3] = a1 / a0; coeffs[i][4] = a2 / a0
        }
        // 重置状态
        state = Array(channelCount.coerceAtLeast(1)) { FloatArray(10 * 4) }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (!enabled) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (!enabled || channelCount == 0 || state.isEmpty()) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        val output = replaceOutputBuffer(remaining)
        val frames = remaining / (2 * channelCount)
        val preampLin = 10.0.pow(preampDb / 20.0)
        var chState = state
        for (frame in 0 until frames) {
            for (ch in 0 until channelCount) {
                // 16-bit 小端：先读低字节再读高字节
                val low = inputBuffer.get().toInt() and 0xFF
                val high = inputBuffer.get().toInt()
                var sample = ((high shl 8) or low).toFloat()
                val st = chState[ch]
                for (band in 0 until 10) {
                    val c = coeffs[band]
                    val x0 = sample
                    val base = band * 4
                    val y = (c[0] * x0 + c[1] * st[base] + c[2] * st[base + 1]
                        - c[3] * st[base + 2] - c[4] * st[base + 3]).toFloat()
                    st[base + 1] = st[base]
                    st[base] = x0
                    st[base + 3] = st[base + 2]
                    st[base + 2] = y
                    sample = y
                }
                var out = (sample * preampLin).toInt()
                out = out.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.put((out and 0xFF).toByte())
                output.put(((out shr 8) and 0xFF).toByte())
            }
        }
        output.flip()
    }

    override fun onFlush() {
        state.forEach { it.fill(0f) }
    }
}