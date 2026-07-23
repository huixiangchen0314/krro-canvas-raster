package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.colorutils.color.RGB;

/**
 * 常用的线稿检测器工厂方法。
 */
public final class LineArtDetectors {
    private LineArtDetectors() {}

    /** 基于亮度阈值的线稿检测（默认阈值 0.1，0=纯黑，1=纯白） */
    public static LineArtDetector byLuminance(float threshold) {
        return pixel -> RGB.luminance(pixel) < threshold;
    }

    /** 默认检测器：亮度低于 0.1 视为线稿 */
    public static LineArtDetector defaultDetector() {
        return byLuminance(0.1f);
    }
}