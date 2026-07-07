package top.kzre.krro.canvas.raster;

import top.kzre.colorutils.blend.Blends;

public final class RenderLayer {

    private RenderLayer() {}

    /**
     * 使用仿射变换将源缓冲区混合到目标缓冲区（原地修改 dst）。
     * 采用反向映射 + 双线性插值，处理边界超出（透明跳过）。
     *
     * @param dst       目标 RGBA 数组 (w * h * 4)
     * @param src       源 RGBA 数组 (w * h * 4)
     * @param w         宽度
     * @param h         高度
     * @param matrix    正向仿射矩阵 [a b c d tx ty]，即 dst = A * src + t
     * @param blendMode 混合模式名称 (如 "normal", "multiply")
     * @param opacity   全局透明度乘数 (0..1)
     */
    public static void blendTransformed(float[] dst, float[] src,
                                        int w, int h,
                                        float[] matrix,
                                        String blendMode, float opacity) {
        // 解析正向矩阵
        float ma = matrix[0], mb = matrix[1], mc = matrix[2], md = matrix[3], mtx = matrix[4], mty = matrix[5];
        float det = ma * md - mb * mc;
        if (Math.abs(det) < 1e-10f) return; // 退化矩阵，不绘制

        // 逆矩阵（目标 -> 源）
        float invA = md / det;
        float invB = -mb / det;
        float invC = -mc / det;
        float invD = ma / det;
        float invTx = (mc * mty - md * mtx) / det;
        float invTy = (mb * mtx - ma * mty) / det;

        // 临时数组复用
        float[] bg = new float[4];
        float[] srcPixel = new float[4];

        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                // 反向映射
                float sx = invA * dx + invC * dy + invTx;
                float sy = invB * dx + invD * dy + invTy;

                // 边界检查（需保证有右/下邻居进行插值）
                if (sx < 0 || sx >= w - 1 || sy < 0 || sy >= h - 1) continue;

                int sx0 = (int) sx;
                int sy0 = (int) sy;
                float fx = sx - sx0;
                float fy = sy - sy0;
                int sx1 = sx0 + 1;
                int sy1 = sy0 + 1;

                int idx00 = (sy0 * w + sx0) * 4;
                int idx01 = (sy0 * w + sx1) * 4;
                int idx10 = (sy1 * w + sx0) * 4;
                int idx11 = (sy1 * w + sx1) * 4;

                // 双线性插值各通道
                float r = lerp(lerp(src[idx00], src[idx01], fx),
                        lerp(src[idx10], src[idx11], fx), fy);
                float g = lerp(lerp(src[idx00+1], src[idx01+1], fx),
                        lerp(src[idx10+1], src[idx11+1], fx), fy);
                float b = lerp(lerp(src[idx00+2], src[idx01+2], fx),
                        lerp(src[idx10+2], src[idx11+2], fx), fy);
                float a = lerp(lerp(src[idx00+3], src[idx01+3], fx),
                        lerp(src[idx10+3], src[idx11+3], fx), fy);

                // 目标背景
                int dstIdx = (dy * w + dx) * 4;
                bg[0] = dst[dstIdx];
                bg[1] = dst[dstIdx+1];
                bg[2] = dst[dstIdx+2];
                bg[3] = dst[dstIdx+3];

                // 源像素（应用全局透明度）
                srcPixel[0] = r;
                srcPixel[1] = g;
                srcPixel[2] = b;
                srcPixel[3] = a * opacity;

                // 使用 color-utils 混合
                float[] blended = Blends.blendWithAlpha(blendMode, bg, srcPixel);
                dst[dstIdx]   = blended[0];
                dst[dstIdx+1] = blended[1];
                dst[dstIdx+2] = blended[2];
                dst[dstIdx+3] = blended[3];
            }
        }
    }

    private static float lerp(float v0, float v1, float t) {
        return v0 + (v1 - v0) * t;
    }
}