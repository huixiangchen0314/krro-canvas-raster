package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.canvas.core.Mask;
import top.kzre.krro.util.tile.Canvas;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.*;

/**
 * 洪水填充执行器，负责根据 {@link FloodFillRequest} 组装判定与动作，
 * 执行扫描线/非连续填充算法，并收集脏瓦片，返回填充结果。
 * <p>
 * 包含扩展后处理，暂未实现间隙封闭。
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
     * @param request 填充请求
     * @return 填充结果
     */
    public FloodFillResult execute(FloodFillRequest request) {
        Canvas canvas = request.getTargetCanvas();
        int tileSize = canvas.getTileSize();
        int imgWidth = request.getWidth();
        int imgHeight = request.getHeight();

        // 1. 计算实际填充范围：图像尺寸与现有瓦片覆盖范围的并集
        int minX = 0, minY = 0, maxX = imgWidth - 1, maxY = imgHeight - 1;
        if (canvas.tileCount() > 0) {
            int tMinX = canvas.getMinTileX() * tileSize;
            int tMinY = canvas.getMinTileY() * tileSize;
            int tMaxX = canvas.getMaxTileX() * tileSize + tileSize - 1;
            int tMaxY = canvas.getMaxTileY() * tileSize + tileSize - 1;
            minX = Math.min(minX, tMinX);
            minY = Math.min(minY, tMinY);
            maxX = Math.max(maxX, tMaxX);
            maxY = Math.max(maxY, tMaxY);
        }

        // 2. 种子坐标钳制到并集范围内
        int seedX = (int) Math.round(request.getSeedX());
        int seedY = (int) Math.round(request.getSeedY());
        seedX = Math.max(minX, Math.min(maxX, seedX));
        seedY = Math.max(minY, Math.min(maxY, seedY));

        // 3. 提取种子颜色
        float[] seedColor = extractSeedColor(request, seedX, seedY);
        int channels = canvas.getChannels();

        // 4. 构建判定谓词
        FillPredicate predicate = buildPredicate(request, seedColor, channels);

        // 5. 构建写入动作
        FillAction action = buildAction(request, predicate);

        // 6. 执行填充算法，并同步收集脏瓦片和填充蒙版
        Set<Long> dirtyTiles = new HashSet<>();
        int totalWidth = maxX - minX + 1;
        int totalHeight = maxY - minY + 1;
        BitSet filledMask = new BitSet(totalWidth * totalHeight);
        long filled;

        if (request.isContiguous()) {
            filled = fillContiguous(canvas, minX, minY, maxX, maxY, tileSize,
                    seedX, seedY, request.getFillColor(),
                    predicate, action, dirtyTiles, filledMask);
        } else {
            filled = fillDiscontiguous(canvas, minX, minY, maxX, maxY, tileSize,
                    request.getFillColor(), predicate, action, dirtyTiles, filledMask);
        }

        // 7. 扩展后处理
        if (filled > 0 && request.getExpandRadius() > 0) {
            filled += applyExpand(request, minX, minY, maxX, maxY, tileSize,
                    request.getFillColor(), filledMask, dirtyTiles);
        }

        boolean changed = filled > 0;
        return new FloodFillResult(canvas, dirtyTiles, changed);
    }

    // ---------- 种子颜色提取 ----------
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
                rgba[0] = gray;
                rgba[1] = gray;
                if (targetCh > 2) rgba[2] = gray;
                if (targetCh > 3) rgba[3] = 1.0f;
                return rgba;
            }
            throw new UnsupportedOperationException(
                    "Unsupported channel conversion: ref=" + refCh + " target=" + targetCh);
        }
        float[] seed = new float[target.getChannels()];
        target.getPixel(seedX, seedY, seed);
        return seed;
    }

    // ---------- 构建判定谓词 ----------
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

    // ---------- 构建写入动作 ----------
    private FillAction buildAction(FloodFillRequest req, FillPredicate predicate) {
        FillAction inner;
        if (req.isAntiAlias()) {
            inner = FillActions.ssaa2x2(predicate);
        } else {
            inner = FillActions.direct();
        }

        String blendMode = req.getBlendMode();
        if (blendMode != null) {
            inner = FillActions.blend(inner, blendMode);
        }
        if (req.isProtectLineArt()) {
            inner = FillActions.protectLineArt(inner, req.getLineArtDetector());
        }
        return inner;
    }

    // ---------- 扫描线连续填充 ----------
    private long fillContiguous(Canvas canvas,
                                int minX, int minY, int maxX, int maxY, int tileSize,
                                int seedX, int seedY,
                                float[] fillColor,
                                FillPredicate predicate, FillAction action,
                                Set<Long> dirtyTiles, BitSet filledMask) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BitSet visited = new BitSet(width * height);
        Deque<Integer> queueX = new ArrayDeque<>();
        Deque<Integer> queueY = new ArrayDeque<>();

        int relSeedX = seedX - minX;
        int relSeedY = seedY - minY;
        if (!predicate.shouldFill(seedX, seedY, canvas)) return 0;

        visited.set(relSeedY * width + relSeedX);
        queueX.addLast(seedX);
        queueY.addLast(seedY);
        long filled = 0;

        while (!queueX.isEmpty()) {
            int x = queueX.removeFirst();
            int y = queueY.removeFirst();

            int left = x;
            while (left > minX && !isVisited(left - 1, y, minX, minY, width, visited)
                    && predicate.shouldFill(left - 1, y, canvas)) {
                left--;
            }
            int right = x;
            while (right < maxX && !isVisited(right + 1, y, minX, minY, width, visited)
                    && predicate.shouldFill(right + 1, y, canvas)) {
                right++;
            }

            for (int ix = left; ix <= right; ix++) {
                action.apply(ix, y, canvas, fillColor);
                setVisited(ix, y, minX, minY, width, visited);
                markMask(ix, y, minX, minY, width, filledMask);
                markDirty(ix, y, tileSize, dirtyTiles);
                filled++;
            }

            if (y > minY) scanLine(canvas, minX, minY, maxX, maxY, width, visited, left, right, y - 1, predicate, queueX, queueY);
            if (y < maxY) scanLine(canvas, minX, minY, maxX, maxY, width, visited, left, right, y + 1, predicate, queueX, queueY);
        }
        return filled;
    }

    private void scanLine(Canvas canvas,
                          int minX, int minY, int maxX, int maxY,
                          int width, BitSet visited,
                          int left, int right, int y,
                          FillPredicate predicate,
                          Deque<Integer> queueX, Deque<Integer> queueY) {
        int x = left;
        while (x <= right) {
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
            int mid = (start + end) / 2;
            queueX.addLast(mid);
            queueY.addLast(y);
            // 注意：不在这里标记 visited，否则主循环的左右扩展会失败
        }
    }

    // ---------- 非连续全局替换 ----------
    private long fillDiscontiguous(Canvas canvas,
                                   int minX, int minY, int maxX, int maxY, int tileSize,
                                   float[] fillColor,
                                   FillPredicate predicate, FillAction action,
                                   Set<Long> dirtyTiles, BitSet filledMask) {
        int width = maxX - minX + 1;
        long filled = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (predicate.shouldFill(x, y, canvas)) {
                    action.apply(x, y, canvas, fillColor);
                    markMask(x, y, minX, minY, width, filledMask);
                    markDirty(x, y, tileSize, dirtyTiles);
                    filled++;
                }
            }
        }
        return filled;
    }

    // ---------- 扩展后处理 ----------
    private long applyExpand(FloodFillRequest request,
                             int minX, int minY, int maxX, int maxY, int tileSize,
                             float[] fillColor,
                             BitSet filledMask,
                             Set<Long> dirtyTiles) {
        int intRadius = Math.round(request.getExpandRadius());
        if (intRadius <= 0) return 0;

        Canvas canvas = request.getTargetCanvas();
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;

        // 膨胀
        BitSet expandedMask = new BitSet(width * height);
        for (int idx = filledMask.nextSetBit(0); idx >= 0; idx = filledMask.nextSetBit(idx + 1)) {
            int fy = idx / width;
            int fx = idx % width;
            int worldX = fx + minX;
            int worldY = fy + minY;
            for (int dy = -intRadius; dy <= intRadius; dy++) {
                int wy = worldY + dy;
                if (wy < minY || wy > maxY) continue;
                for (int dx = -intRadius; dx <= intRadius; dx++) {
                    int wx = worldX + dx;
                    if (wx < minX || wx > maxX) continue;
                    expandedMask.set((wy - minY) * width + (wx - minX));
                }
            }
        }

        expandedMask.andNot(filledMask);
        if (expandedMask.isEmpty()) return 0;

        // 构建扩展专用谓词（无颜色匹配）
        FillPredicate expandPredicate = buildExpandPredicate(request, canvas.getChannels());
        // 扩展动作：直接替换，可加线稿保护
        FillAction expandAction = FillActions.direct();
        if (request.isProtectLineArt()) {
            expandAction = FillActions.protectLineArt(expandAction, request.getLineArtDetector());
        }

        long count = 0;
        for (int idx = expandedMask.nextSetBit(0); idx >= 0; idx = expandedMask.nextSetBit(idx + 1)) {
            int fy = idx / width;
            int fx = idx % width;
            int wx = fx + minX;
            int wy = fy + minY;

            if (expandPredicate.shouldFill(wx, wy, canvas)) {
                expandAction.apply(wx, wy, canvas, fillColor);
                markDirty(wx, wy, tileSize, dirtyTiles);
                count++;
            }
        }
        return count;
    }

    private FillPredicate buildExpandPredicate(FloodFillRequest req, int channels) {
        FillPredicate p = FillPredicate.alwaysTrue(); // 不含颜色匹配
        Mask mask = req.getMask();
        if (mask != null) {
            p = FillPredicate.and(p, FillPredicate.mask(mask));
        }
        if (req.isProtectOpacity()) {
            p = FillPredicate.and(p, FillPredicate.protectOpacity(channels - 1, 0.0f));
        }
        if (req.isProtectLineArt()) {
            p = FillPredicate.and(p, FillPredicate.protectLineArt(req.getLineArtDetector()));
        }
        return p;
    }

    // ---------- 辅助方法 ----------
    private static boolean isVisited(int worldX, int worldY, int minX, int minY, int width, BitSet visited) {
        return visited.get((worldY - minY) * width + (worldX - minX));
    }

    private static void setVisited(int worldX, int worldY, int minX, int minY, int width, BitSet visited) {
        visited.set((worldY - minY) * width + (worldX - minX));
    }

    private static void markMask(int worldX, int worldY, int minX, int minY, int width, BitSet mask) {
        mask.set((worldY - minY) * width + (worldX - minX));
    }

    private static void markDirty(int worldX, int worldY, int tileSize, Set<Long> dirtyTiles) {
        int tx = TiledCanvas.tileX(worldX, tileSize);
        int ty = TiledCanvas.tileY(worldY, tileSize);
        dirtyTiles.add(TiledCanvas.pack(tx, ty));
    }
}