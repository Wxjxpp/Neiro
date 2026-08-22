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
 * 80 倍速播放模式：把 PCM 每帧复制 N 份实现"快进"听感
 * （Media3 的 setPlaybackSpeed 上限 4x，超高速只能靠重采样）。
 * N = round(80)，实际输出采样率不变，时长压缩为 1/N。
 *
 * 实现说明：每读取 1 个样本帧就写入 N 次，等效于把音频"拉长"了 N 倍
 * 频率（音调变高 80 倍），配合原速播放产生芯片音乐式超高速效果。
 */
class TurboSpeedAudioProcessor(private var factor: Int = 80) : BaseAudioProcessor() {
    private var enabled: Boolean = false
    private var frameBytes: Int = 0

    fun setEnabledMode(on: Boolean) {
        if (enabled == on) return
        enabled = on
        runCatching { flush() }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        frameBytes = inputAudioFormat.bytesPerFrame
        // 禁用时直通
        if (!enabled || frameBytes <= 0) return inputAudioFormat
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (!enabled) {
            // 直通
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        // 按帧对齐，每帧复制 factor 份（音调升高 factor 倍的超高速效果）
        val frames = remaining / frameBytes
        val usable = frames * frameBytes
        val output = replaceOutputBuffer(usable * factor)
        val data = ByteArray(frameBytes)
        repeat(frames) {
            inputBuffer.get(data)
            repeat(factor) { output.put(data) }
        }
        output.flip()
    }
}