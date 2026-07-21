package top.kzre.krro.canvas.raster;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.tile.Tile;
import top.kzre.krro.util.tile.TiledCanvas;
import top.kzre.krro.util.math.KMath;

import java.util.HashSet;
import java.util.Set;

/**
 * 将 TiledCanvas 混合到目标浮点数组（RGBA 连续存储）中。
 * 支持 2D 仿射变换、混合模式和透明度，并允许指定目标脏瓦片以限制更新区域。
 */
public final class TiledCanvasRenderer {
    private TiledCanvasRenderer() {}

    /**
     * 将 TiledCanvas 混合到目标浮点数组中。
     *
     * @param dst         目标 RGBA 数组 (dstW * dstH * 4)
     * @param dstW        目标宽度
     * @param dstH        目标高度
     * @param canvas      源 TiledCanvas
     * @param matrix2d    2D 仿射变换矩阵 [a, b, c, d, tx, ty]（列向量约定）
     * @param blendMode   混合模式（来自 Blends 常量）
     * @param opacity     图层透明度 [0, 1]
     * @param dirtyTiles  需要更新的目标瓦片键集（基于 tileSize 划分），
     *                    若为 null 则更新所有目标瓦片；若为空集合则忽略。
     */
    public static void blendTransformedTiled(float[] dst, int dstW, int dstH,
                                             TiledCanvas canvas,
                                             float[] matrix2d, String blendMode, float opacity,
                                             Set<Long> dirtyTiles) {
        if (dst == null) throw new IllegalArgumentException("dst cannot be null");
        if (canvas == null) throw new IllegalArgumentException("canvas cannot be null");
        if (opacity < 0 || opacity > 1) throw new IllegalArgumentException("opacity out of range");
        if (matrix2d == null || matrix2d.length < 6)
            throw new IllegalArgumentException("matrix2d must be a 6-element float array");

        int tileSize = canvas.getTileSize();
        if (tileSize <= 0) return;

        // 空脏集合，直接返回
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        // 计算逆矩阵（源 → 目标）
        float[] invMatrix = KMath.mat2dInvert(matrix2d);
        if (invMatrix == null) return; // 奇异矩阵，忽略

        // 如果 dirtyTiles 为 null，生成所有目标瓦片
        Set<Long> targetTiles = dirtyTiles;
        if (targetTiles == null) {
            int tilesX = (dstW + tileSize - 1) / tileSize;
            int tilesY = (dstH + tileSize - 1) / tileSize;
            targetTiles = new HashSet<>();
            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    targetTiles.add(TiledCanvas.pack(tx, ty));
                }
            }
        }

        // 复用临时数组
        float[] bg = new float[4];
        float[] fg = new float[4];
        float[] c00 = new float[4];
        float[] c10 = new float[4];
        float[] c01 = new float[4];
        float[] c11 = new float[4];

        // 瓦片缓存
        long cachedKey = -1L;
        Tile cachedTile = null;

        // 遍历每个脏瓦片
        for (long key : targetTiles) {
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);

            // 目标瓦片在 dst 中的像素范围（裁剪到边界）
            int startX = Math.max(tx * tileSize, 0);
            int startY = Math.max(ty * tileSize, 0);
            int endX = Math.min(startX + tileSize, dstW);
            int endY = Math.min(startY + tileSize, dstH);

            // 若经过钳制后为空，则跳过该瓦片
            if (startX >= endX || startY >= endY) continue;

            // 循环目标像素
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    // 通过逆矩阵映射到源坐标
                    float sx = invMatrix[0] * x + invMatrix[2] * y + invMatrix[4];
                    float sy = invMatrix[1] * x + invMatrix[3] * y + invMatrix[5];

                    // 计算四个角的整数坐标和权重
                    int x0 = (int) Math.floor(sx);
                    int y0 = (int) Math.floor(sy);
                    float fx = sx - x0;
                    float fy = sy - y0;
                    int x1 = x0 + 1;
                    int y1 = y0 + 1;

                    // 计算四个角所属的瓦片键
                    long k00 = packForTile(x0, y0, tileSize);
                    long k10 = packForTile(x1, y0, tileSize);
                    long k01 = packForTile(x0, y1, tileSize);
                    long k11 = packForTile(x1, y1, tileSize);

                    // 如果四个角在同一瓦片，只需获取一次
                    if (k00 == k10 && k00 == k01 && k00 == k11) {
                        Tile tile;
                        // 缓存命中检查
                        if (k00 == cachedKey && cachedTile != null) {
                            tile = cachedTile;
                        } else {
                            int tx0 = TiledCanvas.unpackTx(k00);
                            int ty0 = TiledCanvas.unpackTy(k00);
                            tile = canvas.getTile(tx0, ty0);
                            cachedKey = k00;
                            cachedTile = tile;
                        }
                        if (tile != null) {
                            float[] data = tile.getPixelsSnapshot();
                            readPixel(data, x0, y0, tileSize, c00);
                            readPixel(data, x1, y0, tileSize, c10);
                            readPixel(data, x0, y1, tileSize, c01);
                            readPixel(data, x1, y1, tileSize, c11);
                        } else {
                            setZero(c00);
                            setZero(c10);
                            setZero(c01);
                            setZero(c11);
                        }
                    } else {
                        // 四个角跨多个瓦片，分别获取
                        readPixelFromCanvas(canvas, x0, y0, tileSize, c00);
                        readPixelFromCanvas(canvas, x1, y0, tileSize, c10);
                        readPixelFromCanvas(canvas, x0, y1, tileSize, c01);
                        readPixelFromCanvas(canvas, x1, y1, tileSize, c11);
                    }

                    // 双线性插值
                    for (int i = 0; i < 4; i++) {
                        float top = c00[i] + (c10[i] - c00[i]) * fx;
                        float bottom = c01[i] + (c11[i] - c01[i]) * fx;
                        fg[i] = top + (bottom - top) * fy;
                    }

                    // 透明度应用
                    fg[3] *= opacity;

                    // 如果源像素完全透明，跳过
                    if (fg[3] == 0f) continue;

                    // 读取目标像素
                    int idx = (y * dstW + x) * 4;
                    bg[0] = dst[idx];
                    bg[1] = dst[idx + 1];
                    bg[2] = dst[idx + 2];
                    bg[3] = dst[idx + 3];

                    // 混合并写回
                    float[] blended = Blends.blendWithAlpha(blendMode, bg, fg);
                    dst[idx]     = blended[0];
                    dst[idx + 1] = blended[1];
                    dst[idx + 2] = blended[2];
                    dst[idx + 3] = blended[3];
                }
            }
        }
    }

    // ---------- 辅助方法 ----------

    /** 从画布读取一个像素（整数坐标）到 out 数组 */
    private static void readPixelFromCanvas(TiledCanvas canvas, int px, int py, int tileSize, float[] out) {
        int tx = TiledCanvas.tileX(px, tileSize);
        int ty = TiledCanvas.tileY(py, tileSize);
        Tile tile = canvas.getTile(tx, ty);
        if (tile == null) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        float[] data = tile.getPixelsSnapshot();
        readPixel(data, px, py, tileSize, out);
    }

    /** 从瓦片数据中读取像素（整数坐标）到 out 数组 */
    private static void readPixel(float[] data, int px, int py, int tileSize, float[] out) {
        int lx = TiledCanvas.localX(px, tileSize);
        int ly = TiledCanvas.localY(py, tileSize);
        int idx = (ly * tileSize + lx) * TiledCanvas.CHANNELS;
        out[0] = data[idx];
        out[1] = data[idx + 1];
        out[2] = data[idx + 2];
        out[3] = data[idx + 3];
    }

    /** 将数组设置为零 */
    private static void setZero(float[] out) {
        out[0] = out[1] = out[2] = out[3] = 0f;
    }

    /** 打包瓦片坐标 */
    private static long packForTile(int px, int py, int tileSize) {
        int tx = TiledCanvas.tileX(px, tileSize);
        int ty = TiledCanvas.tileY(py, tileSize);
        return TiledCanvas.pack(tx, ty);
    }
}