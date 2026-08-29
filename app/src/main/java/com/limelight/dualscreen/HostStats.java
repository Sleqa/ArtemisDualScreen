package com.limelight.dualscreen;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Snapshot of the host PC's system counters as reported by Vibepollo's
 * {@code GET /api/host/stats} endpoint. Counters the host cannot sample come back as -1.
 */
public class HostStats {
    public float cpuPercent = -1f;
    public float cpuTempC = -1f;
    public long ramUsedBytes = 0;
    public long ramTotalBytes = 0;
    public double ramPercent = -1;
    public float gpuPercent = -1f;
    public float gpuEncoderPercent = -1f;
    public float gpuTempC = -1f;
    public long vramUsedBytes = 0;
    public long vramTotalBytes = 0;
    public double vramPercent = -1;
    public double netRxBps = -1;
    public double netTxBps = -1;

    public static HostStats fromJson(String body) throws JSONException {
        JSONObject json = new JSONObject(body);
        HostStats stats = new HostStats();
        stats.cpuPercent = (float) json.optDouble("cpu_percent", -1);
        stats.cpuTempC = (float) json.optDouble("cpu_temp_c", -1);
        stats.ramUsedBytes = json.optLong("ram_used_bytes", 0);
        stats.ramTotalBytes = json.optLong("ram_total_bytes", 0);
        stats.ramPercent = json.optDouble("ram_percent", -1);
        stats.gpuPercent = (float) json.optDouble("gpu_percent", -1);
        stats.gpuEncoderPercent = (float) json.optDouble("gpu_encoder_percent", -1);
        stats.gpuTempC = (float) json.optDouble("gpu_temp_c", -1);
        stats.vramUsedBytes = json.optLong("vram_used_bytes", 0);
        stats.vramTotalBytes = json.optLong("vram_total_bytes", 0);
        stats.vramPercent = json.optDouble("vram_percent", -1);
        stats.netRxBps = json.optDouble("net_rx_bps", -1);
        stats.netTxBps = json.optDouble("net_tx_bps", -1);
        return stats;
    }

    public boolean hasCpu() {
        return cpuPercent >= 0f;
    }

    public boolean hasGpu() {
        return gpuPercent >= 0f;
    }

    public boolean hasRam() {
        return ramTotalBytes > 0;
    }

    public float ramUsedGb() {
        return ramUsedBytes / 1073741824f;
    }

    public float ramFraction() {
        if (ramTotalBytes <= 0) {
            return -1f;
        }
        return (float) ramUsedBytes / (float) ramTotalBytes;
    }
}
