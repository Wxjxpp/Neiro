package com.wxjxpp.neiro.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * 8-bit 播放模式：把 16-bit PCM 样本量化到 8-bit 精度（高 8 位保留），
 * 复古游戏机音质。仅处理 16-bit PCM 编码，其他格式原样透传。
 */
class EightBitAudioProcessor : BaseAudioProcessor() {
    private var enabled: Boolean = false
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    fun setEnabledMode(on: Boolean) {
        if (enabled == on) return
        enabled = on
        runCatching { flush() }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // 未启用或非 16-bit PCM 时直通（保持原格式让音频正常走通）
        val isPcm16 = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!enabled || !isPcm16) return inputAudioFormat
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        if (!enabled) {
            // 直通
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        val output = replaceOutputBuffer(remaining)
        while (inputBuffer.hasRemaining()) {
            inputBuffer.get() // 低字节（16-bit 小端）
            val high = inputBuffer.get().toInt() and 0xFF
            // 8-bit 量化：低字节清零，只留高 8 位
            output.put(0)
            output.put(high.toByte())
        }
        output.flip()
    }
}

/**
 * 80 倍速播放模式：抽帧降采样实现时间压缩——每 N 帧只保留 1 帧，
 * 播放时长压缩为 1/N（等效 80 倍速快进），音高保持不变、人耳可闻。
 *
 * 注意：不能用"帧复制"实现——复制会把音调抬高 N 倍（80 倍即 ~3.5MHz），
 * 落在超声波段，人耳完全听不到（无声 bug 的根因）。
 */
class TurboSpeedAudioProcessor(private var factor: Int = 80) : BaseAudioProcessor() {
    private var enabled: Boolean = false
    private var frameBytes: Int = 0
    /** 跨缓冲区的帧计数（抽帧按全局帧号取模，避免每缓冲都从头开始）。 */
    private var frameCounter: Long = 0L

    fun setEnabledMode(on: Boolean) {
        if (enabled == on) return
        enabled = on
        frameCounter = 0L
        runCatching { flush() }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        frameBytes = inputAudioFormat.bytesPerFrame
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (!enabled || frameBytes <= 0) {
            // 直通
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        // 抽帧：全局帧号 % factor == 0 的帧保留，其余丢弃
        val frames = remaining / frameBytes
        val usable = frames * frameBytes
        val kept = ArrayList<Int>(frames / factor + 1)
        for (i in 0 until frames) {
            if (frameCounter % factor == 0L) kept += i
            frameCounter++
        }
        val startPos = inputBuffer.position()
        val output = replaceOutputBuffer(kept.size * frameBytes)
        val data = ByteArray(frameBytes)
        for (index in kept) {
            inputBuffer.position(startPos + index * frameBytes)
            inputBuffer.get(data)
            output.put(data)
        }
        // 输入必须全部消费（含被丢弃的帧与尾部不足一帧的字节），否则会卡住管线
        inputBuffer.position(startPos + remaining)
        output.flip()
    }
}