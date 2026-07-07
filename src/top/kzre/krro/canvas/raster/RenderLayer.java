package top.kzre.krro.canvas.raster;

import top.kzre.colorutils.blend.Blends;

public final class RenderLayer {

    private RenderLayer() {}

    // ── 公开全画布混合 ──────────────────────
    public static void blendTransformed(float[] dst, float[] src,
                                        int w, int h,
                                        float[] matrix,
                                        String blendMode, float opacity) {
        blendRegion(dst, src, w, h, matrix, blendMode, opacity,
                0, 0, w, h);
    }

    // ── 脏矩形混合 ─────────────────────────
    public static void blendTransformedDirty(float[] dst, float[] src,
                                             int w, int h,
                                             float[] matrix,
                                             String blendMode, float opacity,
                                             int[] dirtyRect) {
        if (dirtyRect == null || dirtyRect.length != 4 ||
                dirtyRect[2] <= 0 || dirtyRect[3] <= 0) {
            blendTransformed(dst, src, w, h, matrix, blendMode, opacity);
            return;
        }

        int dx = Math.max(dirtyRect[0], 0);
        int dy = Math.max(dirtyRect[1], 0);
        int endX = Math.min(dx + dirtyRect[2], w);
        int endY = Math.min(dy + dirtyRect[3], h);

        if (dx >= endX || dy >= endY) return;

        blendRegion(dst, src, w, h, matrix, blendMode, opacity,
                dx, dy, endX, endY);
    }

    // ── 核心区域混合（内部实现） ────────────
    private static void blendRegion(float[] dst, float[] src,
                                    int w, int h,
                                    float[] matrix,
                                    String blendMode, float opacity,
                                    int startX, int startY,
                                    int endX, int endY) {
        // 矩阵求逆
        float ma = matrix[0], mb = matrix[1], mc = matrix[2], md = matrix[3], mtx = matrix[4], mty = matrix[5];
        float det = ma * md - mb * mc;
        if (Math.abs(det) < 1e-10f) return;

        float invA = md / det;
        float invB = -mb / det;
        float invC = -mc / det;
        float invD = ma / det;
        float invTx = (mc * mty - md * mtx) / det;
        float invTy = (mb * mtx - ma * mty) / det;

        float[] bg = new float[4];
        float[] srcPixel = new float[4];

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                float sx = invA * x + invC * y + invTx;
                float sy = invB * x + invD * y + invTy;

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

                float r = lerp(lerp(src[idx00], src[idx01], fx),
                        lerp(src[idx10], src[idx11], fx), fy);
                float g = lerp(lerp(src[idx00+1], src[idx01+1], fx),
                        lerp(src[idx10+1], src[idx11+1], fx), fy);
                float b = lerp(lerp(src[idx00+2], src[idx01+2], fx),
                        lerp(src[idx10+2], src[idx11+2], fx), fy);
                float a = lerp(lerp(src[idx00+3], src[idx01+3], fx),
                        lerp(src[idx10+3], src[idx11+3], fx), fy);

                int dstIdx = (y * w + x) * 4;
                bg[0] = dst[dstIdx];
                bg[1] = dst[dstIdx+1];
                bg[2] = dst[dstIdx+2];
                bg[3] = dst[dstIdx+3];

                srcPixel[0] = r;
                srcPixel[1] = g;
                srcPixel[2] = b;
                srcPixel[3] = a * opacity;

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