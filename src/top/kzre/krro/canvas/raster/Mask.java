package top.kzre.krro.canvas.raster;

/**
 * 蒙板处理工具，将蒙板浮点数组应用到图层像素缓冲区上。
 */
public final class Mask {

    private Mask() {}

    /**
     * 将蒙板应用到源 RGBA 数据上，返回新的 RGBA 数组。
     * 每个像素的 alpha 乘以蒙板值（灰度，0~1），RGB 保持不变。
     *
     * @param srcData  源像素数据，RGBA 格式，长度 = w * h * 4
     * @param maskData 蒙板数据，单通道，长度 = w * h，值应介于 0~1
     * @return 应用蒙板后的新 RGBA 数组
     */
    public static float[] applyMask(float[] srcData, float[] maskData) {
        int len = srcData.length;
        float[] dst = new float[len];
        for (int i = 0; i < len; i += 4) {
            int maskIdx = i / 4;
            float maskVal = maskData[maskIdx];
            // 可选钳位，但调用方应保证数据有效
            if (maskVal < 0.0f) maskVal = 0.0f;
            if (maskVal > 1.0f) maskVal = 1.0f;

            dst[i]     = srcData[i];
            dst[i + 1] = srcData[i + 1];
            dst[i + 2] = srcData[i + 2];
            dst[i + 3] = srcData[i + 3] * maskVal;
        }
        return dst;
    }
}