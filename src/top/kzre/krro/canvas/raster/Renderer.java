package top.kzre.krro.canvas.raster;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.TiledCanvasUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Renderer {

    private Renderer() {}
    private static final float[] ZERO = {0, 0, 0, 0};

    /**
     * 将瓦片画布（源）按矩阵变换后混合到普通浮点数组（目标）上。
     * 目标保持为连续数组是因为它通常作为最终输出或固定的中间缓冲区，
     * 使用 float[] 有利于缓存局部性及后续 GPU 上传。
     *
     * @param dst         目标 RGBA 数组 (dstW * dstH * 4)
     * @param dstW        目标宽度
     * @param dstH        目标高度
     * @param srcTiles    源瓦片映射，键为位编码的 (tx,ty)
     * @param srcTileSize 源瓦片尺寸
     * @param matrix      仿射变换矩阵 (6 元素)
     * @param blendMode   混合模式
     * @param opacity     额外不透明度
     * @param dirtyTiles  需要更新的目标 tile 键集合 (基于 tileSize 划分)
     */
    public static void blendTransformedTiled(float[] dst, int dstW, int dstH,
                                             Map<Long, float[]> srcTiles, int srcTileSize,
                                             float[] matrix, String blendMode, float opacity,
                                             Set<Long> dirtyTiles) {
        // 空集合 -> 没有脏区域，直接返回
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        // 为 null 时生成所有可能的目标瓦片
        if (dirtyTiles == null) {
            int tilesX = (dstW + srcTileSize - 1) / srcTileSize;
            int tilesY = (dstH + srcTileSize - 1) / srcTileSize;
            dirtyTiles = new HashSet<>();
            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    dirtyTiles.add(TiledCanvasUtils.pack(tx, ty));
                }
            }
        }

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

        for (long key : dirtyTiles) {
            int tx = TiledCanvasUtils.unpackTx(key);
            int ty = TiledCanvasUtils.unpackTy(key);

            int startX = tx * srcTileSize;
            int startY = ty * srcTileSize;
            int endX = Math.min(startX + srcTileSize, dstW);
            int endY = Math.min(startY + srcTileSize, dstH);

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    float sx = invA * x + invC * y + invTx;
                    float sy = invB * x + invD * y + invTy;

                    // 无需边界检查，任意坐标均从 tile 采样（不存在则为透明）
                    sampleTiled(srcTiles, srcTileSize, sx, sy, srcPixel);
                    if (srcPixel[3] == 0f) continue; // 完全透明，跳过

                    srcPixel[3] *= opacity;

                    int idx = (y * dstW + x) * 4;
                    bg[0] = dst[idx];
                    bg[1] = dst[idx + 1];
                    bg[2] = dst[idx + 2];
                    bg[3] = dst[idx + 3];

                    float[] blended = Blends.blendWithAlpha(blendMode, bg, srcPixel);
                    dst[idx]     = blended[0];
                    dst[idx + 1] = blended[1];
                    dst[idx + 2] = blended[2];
                    dst[idx + 3] = blended[3];
                }
            }
        }
    }

    /** 从源瓦片映射中采样一个像素（双线性插值），若无数据返回透明 */
    private static void sampleTiled(Map<Long, float[]> tiles, int tileSize,
                                    float sx, float sy, float[] out) {
        int sx0 = (int) Math.floor(sx);
        int sy0 = (int) Math.floor(sy);
        float fx = sx - sx0;
        float fy = sy - sy0;
        int sx1 = sx0 + 1;
        int sy1 = sy0 + 1;

        float[] c00 = getPixel(tiles, tileSize, sx0, sy0);
        float[] c01 = getPixel(tiles, tileSize, sx1, sy0);
        float[] c10 = getPixel(tiles, tileSize, sx0, sy1);
        float[] c11 = getPixel(tiles, tileSize, sx1, sy1);

        for (int i = 0; i < 4; i++) {
            out[i] = lerp(lerp(c00[i], c01[i], fx), lerp(c10[i], c11[i], fx), fy);
        }
    }

    private static float[] getPixel(Map<Long, float[]> tiles, int tileSize, int x, int y) {
        int tx = TiledCanvasUtils.tileX(x, tileSize);
        int ty = TiledCanvasUtils.tileY(y, tileSize);
        long key = TiledCanvasUtils.pack(tx, ty);
        float[] tile = tiles.get(key);
        if (tile == null) return ZERO;
        int localX = TiledCanvasUtils.localX(x, tileSize);
        int localY = TiledCanvasUtils.localY(y, tileSize);
        int idx = 4 * (localY * tileSize + localX);
        return new float[]{tile[idx], tile[idx+1], tile[idx+2], tile[idx+3]};
    }





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