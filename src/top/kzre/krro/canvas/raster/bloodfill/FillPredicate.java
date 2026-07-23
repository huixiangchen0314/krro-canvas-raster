package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.canvas.core.Mask;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;

import java.util.function.Predicate;

public abstract class FillPredicate {

    /** 子类必须实现：判断像素是否可被填充 */
    public abstract boolean shouldFill(int x, int y, Canvas canvas);

    // ── 工厂方法 ──────────────────────────────

    public static FillPredicate alwaysTrue() {
        return AlwaysTrue.INSTANCE;
    }

    public static FillPredicate colorMatch(float[] seedColor,
                                           ColorMatcher matcher,
                                           float tolerance) {
        return new ColorMatch(seedColor, matcher, tolerance);
    }

    public static FillPredicate mask(Mask mask) {
        return mask == null ? alwaysTrue() : new MaskPred(mask);
    }

    public static FillPredicate protectOpacity(int alphaChannelIndex, float minAlpha) {
        return new ProtectOpacity(alphaChannelIndex, minAlpha);
    }

    public static FillPredicate protectLineArt(LineArtDetector detector) {
        return new ProtectLineArt(detector);
    }

    /**
     * 返回一个谓词，要求所有传入的谓词同时满足。
     * 内部使用循环逐一判断，无额外对象嵌套。
     */
    public static FillPredicate and(FillPredicate... predicates) {
        if (predicates == null || predicates.length == 0) return alwaysTrue();
        if (predicates.length == 1) return predicates[0];
        return new AndAll(predicates.clone());
    }



    /**
     * 实例方法：返回一个组合谓词，要求当前谓词和指定谓词同时成立（逻辑 AND）。
     * 支持链式调用，例如：colorMatch(...).and(mask(...)).and(protectOpacity(...))
     */
    public FillPredicate and(FillPredicate other) {
        if (other == AlwaysTrue.INSTANCE) return this;
        if (this == AlwaysTrue.INSTANCE) return other;
        return new And(this, other);
    }


    // ── 模板方法：从池中获取像素数组，使用后自动归还 ──
    protected final boolean withPixel(int x, int y, Canvas canvas, Predicate<float[]> test) {
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] pixel = pool.acquire();
        try {
            canvas.getPixel(x, y, pixel);
            return test.test(pixel);
        } finally {
            pool.release(pixel);
        }
    }


    // ────── 内部实现 ──────

    private static final class AlwaysTrue extends FillPredicate {
        static final AlwaysTrue INSTANCE = new AlwaysTrue();
        @Override public boolean shouldFill(int x, int y, Canvas canvas) { return true; }
    }

    private static final class ColorMatch extends FillPredicate {
        private final float[] seedColor;
        private final ColorMatcher matcher;
        private final float tolerance;

        ColorMatch(float[] seedColor, ColorMatcher matcher, float tolerance) {
            this.seedColor = seedColor.clone();
            this.matcher = matcher;
            this.tolerance = tolerance;
        }

        @Override public boolean shouldFill(int x, int y, Canvas canvas) {
            return withPixel(x, y, canvas, pixel ->
                    matcher.match(pixel, seedColor, tolerance)
            );
        }
    }

    private static final class MaskPred extends FillPredicate {
        private final Mask mask;
        MaskPred(Mask mask) { this.mask = mask; }
        @Override public boolean shouldFill(int x, int y, Canvas canvas) {
            return mask.getValue(x + 0.5, y + 0.5) > 0f;
        }
    }

    private static final class ProtectOpacity extends FillPredicate {
        private final int alphaIdx;
        private final float minAlpha;

        ProtectOpacity(int alphaIdx, float minAlpha) {
            this.alphaIdx = alphaIdx;
            this.minAlpha = minAlpha;
        }

        @Override public boolean shouldFill(int x, int y, Canvas canvas) {
            return withPixel(x, y, canvas, pixel -> pixel[alphaIdx] > minAlpha);
        }
    }

    private static final class ProtectLineArt extends FillPredicate {
        private final LineArtDetector detector;

        ProtectLineArt(LineArtDetector detector) { this.detector = detector; }

        @Override public boolean shouldFill(int x, int y, Canvas canvas) {
            return withPixel(x, y, canvas, pixel -> !detector.isLineArt(pixel));
        }
    }

    private static final class And extends FillPredicate {
        private final FillPredicate first;
        private final FillPredicate second;

        And(FillPredicate first, FillPredicate second) {
            this.first = first;
            this.second = second;
        }

        @Override public boolean shouldFill(int x, int y, Canvas canvas) {
            return first.shouldFill(x, y, canvas) && second.shouldFill(x, y, canvas);
        }
    }

    private static final class AndAll extends FillPredicate {
        private final FillPredicate[] predicates;

        AndAll(FillPredicate[] predicates) {
            this.predicates = predicates;
        }

        @Override
        public boolean shouldFill(int x, int y, Canvas canvas) {
            for (FillPredicate p : predicates) {
                if (!p.shouldFill(x, y, canvas)) return false;
            }
            return true;
        }
    }
}