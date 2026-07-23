package top.kzre.krro.canvas.raster.bloodfill;

/**
 * {@link ColorMatcher} 的静态工厂，提供常用的颜色比较策略。
 * 所有返回的匹配器均为无状态纯函数，可安全地在多线程环境下共享。
 */
public final class ColorMatchers {

    private ColorMatchers() {}

    /**
     * 平方欧几里得距离匹配器（所有通道参与计算）。
     * 计算 Σ (p1[i] - p2[i])² ，与 tolerance² 比较。
     * 避免开方运算，性能更优，匹配结果与欧几里得距离完全等价。
     */
    public static ColorMatcher euclideanSquare() {
        return EuclideanSquaredMatcher.INSTANCE;
    }

    /**
     * 通道最大差值匹配器（所有通道参与计算）。
     * 差值 = max( |p1[i] - p2[i]| ) ，当最大差值 ≤ tolerance 时视为匹配。
     * 对容差的理解更直观（每个通道允许的最大偏差）。
     */
    public static ColorMatcher perChannelMax() {
        return PerChannelMaxMatcher.INSTANCE;
    }

    // ── 单例实现 ──

    private static final class EuclideanSquaredMatcher implements ColorMatcher {
        static final EuclideanSquaredMatcher INSTANCE = new EuclideanSquaredMatcher();

        @Override
        public boolean match(float[] pixels1, int offset1,
                             float[] pixels2, int offset2,
                             int channels, float tolerance) {
            float sq = 0f;
            for (int i = 0; i < channels; i++) {
                float d = pixels1[offset1 + i] - pixels2[offset2 + i];
                sq += d * d;
            }
            return sq <= tolerance * tolerance;
        }
    }

    private static final class PerChannelMaxMatcher implements ColorMatcher {
        static final PerChannelMaxMatcher INSTANCE = new PerChannelMaxMatcher();

        @Override
        public boolean match(float[] pixels1, int offset1,
                             float[] pixels2, int offset2,
                             int channels, float tolerance) {
            float maxDiff = 0f;
            for (int i = 0; i < channels; i++) {
                float diff = Math.abs(pixels1[offset1 + i] - pixels2[offset2 + i]);
                if (diff > maxDiff) maxDiff = diff;
            }
            return maxDiff <= tolerance;
        }
    }
}