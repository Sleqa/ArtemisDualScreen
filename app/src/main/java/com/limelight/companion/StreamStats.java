package com.limelight.companion;

/**
 * One-second snapshot of stream performance, published by the video renderer for the
 * second-screen companion. Values that are unavailable are negative.
 */
public final class StreamStats {
    public final int width;
    public final int height;
    public final int targetFps;
    public final float totalFps;
    public final float receivedFps;
    public final float renderedFps;
    public final float networkDropPercent;
    public final int rttMs;
    public final int rttVarianceMs;
    /** Average host encode/processing latency, or -1 when the host does not report it. */
    public final float hostLatencyMs;
    public final float decodeMs;
    public final String codec;
    public final String decoderName;
    /** Combined app rx+tx bandwidth in kilobytes/second, or -1 when unsupported. */
    public final float bandwidthKBps;

    public StreamStats(int width, int height, int targetFps, float totalFps, float receivedFps,
                       float renderedFps, float networkDropPercent, int rttMs, int rttVarianceMs,
                       float hostLatencyMs, float decodeMs, String codec, String decoderName,
                       float bandwidthKBps) {
        this.width = width;
        this.height = height;
        this.targetFps = targetFps;
        this.totalFps = totalFps;
        this.receivedFps = receivedFps;
        this.renderedFps = renderedFps;
        this.networkDropPercent = networkDropPercent;
        this.rttMs = rttMs;
        this.rttVarianceMs = rttVarianceMs;
        this.hostLatencyMs = hostLatencyMs;
        this.decodeMs = decodeMs;
        this.codec = codec;
        this.decoderName = decoderName;
        this.bandwidthKBps = bandwidthKBps;
    }

    /** Short label such as "1080p" / "1440p" / "4K" for the status chip. */
    public String shortResolution() {
        int h = Math.min(width, height) > 0 ? height : 0;
        if (h >= 2160) return "4K";
        if (h > 0) return h + "p";
        return "—";
    }
}
