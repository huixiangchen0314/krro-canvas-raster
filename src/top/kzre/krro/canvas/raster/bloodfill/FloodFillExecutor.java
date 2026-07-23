package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.canvas.core.Mask;
import top.kzre.krro.util.tile.Canvas;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;

/**
 * 洪水填充执行器，负责根据 {@link FloodFillRequest} 组装判定与动作，
 * 执行扫描线/非连续填充算法，并收集脏瓦片，返回填充结果。
 * <p>
 * 当前版本未实现后处理（扩展、间隙封闭）以及所有图层取样（参考画布），
 * 这些特性将作为后续增强加入。
 */
public final class FloodFillExecutor {
    private static final FloodFillExecutor INSTANCE = new FloodFillExecutor();
    private FloodFillExecutor() {}

    public static FloodFillResult fill(FloodFillRequest request) {
        return INSTANCE.execute(Objects.requireNonNull(request));
    }

    /**
     * 执行洪水填充。
     *
     * @param request 填充请求，包含目标画布、种子、颜色、容差等参数
     * @return 填充结果，包含修改后的画布（引用相同实例）、受影响的瓦片集合以及是否发生变更
     */
    public FloodFillResult execute(FloodFillRequest request) {
        Canvas canvas = request.getTargetCanvas();
        int tileSize = canvas.getTileSize();
        int imgWidth = request.getWidth();
        int imgHeight = request.getHeight();

        // ── 1. 计算实际填充范围：图像尺寸与现有瓦片覆盖范围的并集 ──
        int minX = 0;
        int minY = 0;
        int maxX = imgWidth - 1;
        int maxY = imgHeight - 1;

        if (canvas.tileCount() > 0) {
            int tileMinX = canvas.getMinTileX() * tileSize;
            int tileMinY = canvas.getMinTileY() * tileSize;
            int tileMaxX = canvas.getMaxTileX() * tileSize + tileSize - 1;
            int tileMaxY = canvas.getMaxTileY() * tileSize + tileSize - 1;

            minX = Math.min(minX, tileMinX);
            minY = Math.min(minY, tileMinY);
            maxX = Math.max(maxX, tileMaxX);
            maxY = Math.max(maxY, tileMaxY);
        }

        // ── 2. 种子坐标钳制到并集范围内 ──
        int seedX = (int) Math.round(request.getSeedX());
        int seedY = (int) Math.round(request.getSeedY());
        seedX = Math.max(minX, Math.min(maxX, seedX));
        seedY = Math.max(minY, Math.min(maxY, seedY));

        // ── 3. 提取种子颜色（暂仅使用目标画布） ──
        int channels = canvas.getChannels();
        float[] seedColor = extractSeedColor(request, seedX, seedY);

        // ── 4. 构建判定谓词 ──
        FillPredicate predicate = buildPredicate(request, seedColor, channels);

        // ── 5. 构建写入动作 ──
        FillAction action = buildAction(request, predicate);

        // ── 6. 执行填充算法，并同步收集脏瓦片 ──
        Set<Long> dirtyTiles = new HashSet<>();
        long filled;

        if (request.isContiguous()) {
            filled = fillContiguous(canvas, minX, minY, maxX, maxY, tileSize,
                    seedX, seedY, request.getFillColor(),
                    predicate, action, dirtyTiles);
        } else {
            filled = fillDiscontiguous(canvas, minX, minY, maxX, maxY, tileSize,
                    request.getFillColor(), predicate, action, dirtyTiles);
        }

        boolean changed = filled > 0;
        return new FloodFillResult(canvas, dirtyTiles, changed);
    }

    // ═══════════════════════════════════════════════════════════════
    // 私有方法：构建判定谓词
    // ═══════════════════════════════════════════════════════════════

    private FillPredicate buildPredicate(FloodFillRequest req, float[] seedColor, int channels) {
        FillPredicate p = FillPredicate.colorMatch(seedColor, req.getColorMatcher(), req.getTolerance());

        Mask mask = req.getMask();
        if (mask != null) {
            p = FillPredicate.and(p, FillPredicate.mask(mask));
        }

        if (req.isProtectOpacity()) {
            int alphaIdx = channels - 1;
            p = FillPredicate.and(p, FillPredicate.protectOpacity(alphaIdx, 0.0f));
        }

        if (req.isProtectLineArt()) {
            p = FillPredicate.and(p, FillPredicate.protectLineArt(req.getLineArtDetector()));
        }

        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    // 私有方法：构建写入动作
    // ═══════════════════════════════════════════════════════════════

    private FillAction buildAction(FloodFillRequest req, FillPredicate predicate) {
        FillAction inner;

        if (req.isAntiAlias()) {
            // 使用 2×2 SSAA 抗锯齿终端动作
            inner = FillActions.ssaa2x2(predicate);
        } else {
            inner = FillActions.direct();
        }

        // 混合模式装饰器
        String blendMode = req.getBlendMode();
        if (blendMode != null) {
            inner = FillActions.blend(inner, blendMode);
        }

        // 线稿保护装饰器（与谓词中的保护互补，主要用于扩展阶段，这里保留）
        if (req.isProtectLineArt()) {
            inner = FillActions.protectLineArt(inner, req.getLineArtDetector());
        }

        return inner;
    }

    // ═══════════════════════════════════════════════════════════════
    // 扫描线连续填充
    // ═══════════════════════════════════════════════════════════════

    private long fillContiguous(Canvas canvas,
                                int minX, int minY, int maxX, int maxY, int tileSize,
                                int seedX, int seedY,
                                float[] fillColor,
                                FillPredicate predicate, FillAction action,
                                Set<Long> dirtyTiles) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BitSet visited = new BitSet(width * height);
        Deque<Integer> queueX = new ArrayDeque<>();
        Deque<Integer> queueY = new ArrayDeque<>();

        // 种子点转换为相对坐标
        int relSeedX = seedX - minX;
        int relSeedY = seedY - minY;

        if (!predicate.shouldFill(seedX, seedY, canvas)) {
            return 0;
        }

        visited.set(relSeedY * width + relSeedX);
        queueX.addLast(seedX);   // 队列保存世界坐标，便于直接调用 predicate / action
        queueY.addLast(seedY);
        long filled = 0;

        while (!queueX.isEmpty()) {
            int x = queueX.removeFirst();
            int y = queueY.removeFirst();

            // 向左扩展
            int left = x;
            while (left > minX && !isVisited(left - 1, y, minX, minY, width, visited)
                    && predicate.shouldFill(left - 1, y, canvas)) {
                left--;
            }

            // 向右扩展
            int right = x;
            while (right < maxX && !isVisited(right + 1, y, minX, minY, width, visited)
                    && predicate.shouldFill(right + 1, y, canvas)) {
                right++;
            }

            // 填充当前行
            for (int ix = left; ix <= right; ix++) {
                action.apply(ix, y, canvas, fillColor);
                setVisited(ix, y, minX, minY, width, visited);
                markDirty(ix, y, tileSize, dirtyTiles);
                filled++;
            }

            // 检查上一行
            if (y > minY) {
                scanLine(canvas, minX, minY, maxX, maxY, width, visited, left, right, y - 1,
                        predicate, queueX, queueY);
            }
            // 检查下一行
            if (y < maxY) {
                scanLine(canvas, minX, minY, maxX, maxY, width, visited, left, right, y + 1,
                        predicate, queueX, queueY);
            }
        }

        return filled;
    }

    // ── 扫描相邻行，寻找新的连续段并入队 ──
    private void scanLine(Canvas canvas,
                          int minX, int minY, int maxX, int maxY,
                          int width, BitSet visited,
                          int left, int right, int y,
                          FillPredicate predicate,
                          Deque<Integer> queueX, Deque<Integer> queueY) {
        int x = left;
        while (x <= right) {
            // 跳过已访问或不可填充的像素
            while (x <= right && (isVisited(x, y, minX, minY, width, visited)
                    || !predicate.shouldFill(x, y, canvas))) {
                x++;
            }
            if (x > right) break;

            int start = x;
            while (x <= right && !isVisited(x, y, minX, minY, width, visited)
                    && predicate.shouldFill(x, y, canvas)) {
                x++;
            }
            int end = x - 1;
            // 将段中点入队，减少队列大小
            int mid = (start + end) / 2;
            queueX.addLast(mid);
            queueY.addLast(y);
            // ❗️ 注意：不要在这里标记 visited，否则主循环的左右扩展会失败！
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 非连续全局替换
    // ═══════════════════════════════════════════════════════════════

    private long fillDiscontiguous(Canvas canvas,
                                   int minX, int minY, int maxX, int maxY, int tileSize,
                                   float[] fillColor,
                                   FillPredicate predicate, FillAction action,
                                   Set<Long> dirtyTiles) {
        long filled = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (predicate.shouldFill(x, y, canvas)) {
                    action.apply(x, y, canvas, fillColor);
                    markDirty(x, y, tileSize, dirtyTiles);
                    filled++;
                }
            }
        }
        return filled;
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    /** 检查世界坐标 (worldX, worldY) 是否已在 visited 中标记 */
    private static boolean isVisited(int worldX, int worldY, int minX, int minY, int width, BitSet visited) {
        return visited.get((worldY - minY) * width + (worldX - minX));
    }

    /** 将世界坐标 (worldX, worldY) 标记为已访问 */
    private static void setVisited(int worldX, int worldY, int minX, int minY, int width, BitSet visited) {
        visited.set((worldY - minY) * width + (worldX - minX));
    }

    /** 记录该像素所在的瓦片为脏 */
    private static void markDirty(int worldX, int worldY, int tileSize, Set<Long> dirtyTiles) {
        int tx = TiledCanvas.tileX(worldX, tileSize);
        int ty = TiledCanvas.tileY(worldY, tileSize);
        dirtyTiles.add(TiledCanvas.pack(tx, ty));
    }

    /**
     * 提取种子颜色，优先使用参考画布，失败则回退到目标画布。
     * 通道数转换规则：
     * - 同通道：直接读取。
     * - 参考画布为RGBA，目标为单通道：使用参考画布的getOpacity()作为灰度值。
     * - 参考画布为单通道，目标为RGBA：灰度填充至R、G、B，Alpha=1.0。
     * 其他情况回退。
     */
    private float[] extractSeedColor(FloodFillRequest request, int seedX, int seedY) {
        Canvas target = request.getTargetCanvas();
        Canvas ref = request.getReferenceCanvas();
        if (ref != null) {
            int refCh = ref.getChannels();
            int targetCh = target.getChannels();
            if (refCh == targetCh) {
                float[] color = new float[targetCh];
                ref.getPixel(seedX, seedY, color);
                return color;
            } else if (targetCh == 1 && refCh >= 2) {
                // RGBA -> Gray：使用不透明度作为灰度值
                float opacity = ref.getOpacity(seedX, seedY);
                return new float[]{opacity};
            } else if (targetCh >= 2 && refCh == 1) {
                // Gray -> RGBA：灰度填入RGB，Alpha=1.0
                float gray = ref.getGray(seedX, seedY);
                float[] rgba = new float[targetCh];
                rgba[0] = gray;                   // R
                rgba[1] = gray; // G
                if (targetCh > 2) rgba[2] = gray; // B
                if (targetCh > 3) rgba[3] = 1.0f; // A
                return rgba;
            }
            // 其他组合回退到目标画布
            throw new UnsupportedOperationException("Only RGBA or Gray color space s are supported");
        }
        // 默认从目标画布读取
        float[] seed = new float[target.getChannels()];
        target.getPixel(seedX, seedY, seed);
        return seed;
    }
}