package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.canvas.core.Mask;
import top.kzre.krro.util.tile.Canvas;

import java.util.Arrays;
import java.util.Objects;

/**
 * 一次洪水填充操作的完整参数集合，不可变对象。
 * 使用 {@link #newBuilder()} 创建实例，所有字段均通过 Builder 设置，
 * 构建时会进行基本的合法性校验。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * FloodFillRequest request = FloodFillRequest.newBuilder()
 *     .targetCanvas(canvas)
 *     .seed(120, 80)
 *     .fillColor(1.0f, 0.0f, 0.0f, 1.0f)  // 不透明红色
 *     .tolerance(32.0f)
 *     .contiguous(true)
 *     .antiAlias(true)
 *     .mask(selectionMask)
 *     .build();
 * }</pre>
 */
public final class FloodFillRequest {

    // ── 私有字段 ──
    // 注意：所有字段均为 private final，通过 getter 访问
    private final Canvas targetCanvas;
    private final double seedX;
    private final double seedY;
    private final float[] fillColor;      // 总是包含至少 4 个分量（RGBA）
    private final float tolerance;
    private final Canvas referenceCanvas;
    private final boolean contiguous;
    private final boolean antiAlias;
    private final float gapClosingRadius;
    private final boolean protectOpacity;
    private final Mask mask;
    private final String blendMode;        // null 表示替换模式
    private final float expandRadius;
    private final boolean protectLineArt;
    private final LineArtDetector lineArtDetector;
    private final ColorMatcher colorMatcher;
    private final int width;    // 图像宽度（像素）
    private final int height;   // 图像高度（像素）

    /**
     * 私有构造器，由 {@link Builder#build()} 调用。
     * 对参数进行防御性拷贝和合法性检查。
     *
     * @throws NullPointerException 如果 targetCanvas 或 fillColor 为 null
     * @throws IllegalArgumentException 如果 fillColor 长度 < 4，或者 tolerance、gapClosingRadius、expandRadius 为负数
     */
    private FloodFillRequest(Builder builder) {
        this.targetCanvas = Objects.requireNonNull(builder.targetCanvas, "targetCanvas must not be null");
        this.seedX = builder.seedX;
        this.seedY = builder.seedY;
        this.fillColor = builder.fillColor.clone();
        this.tolerance = builder.tolerance;
        this.referenceCanvas = builder.referenceCanvas;
        this.contiguous = builder.contiguous;
        this.antiAlias = builder.antiAlias;
        this.gapClosingRadius = builder.gapClosingRadius;
        this.protectOpacity = builder.protectOpacity;
        this.mask = builder.mask;
        this.blendMode = builder.blendMode;
        this.expandRadius = builder.expandRadius;
        this.protectLineArt = builder.protectLineArt;
        this.lineArtDetector = builder.lineArtDetector;
        this.colorMatcher = builder.colorMatcher;

        // 基本校验
        this.width = builder.width;
        this.height = builder.height;
        // 校验
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }

        Objects.requireNonNull(fillColor, "fillColor must not be null");
        if (fillColor.length < 4) {
            throw new IllegalArgumentException("fillColor must contain at least 4 components (RGBA)");
        }
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must be >= 0");
        }
        if (gapClosingRadius < 0) {
            throw new IllegalArgumentException("gapClosingRadius must be >= 0");
        }
        if (expandRadius < 0) {
            throw new IllegalArgumentException("expandRadius must be >= 0");
        }
    }

    // ── 公有 Getter ──────────────────────────────

    /** 返回目标画布（要填充的图层），不会为 null。 */
    public Canvas getTargetCanvas() {
        return targetCanvas;
    }


    /** 种子点 X 坐标（整数像素，通常从浮点坐标四舍五入得到）。 */
    public double getSeedX() {
        return seedX;
    }

    /** 种子点 Y 坐标（整数像素）。 */
    public double getSeedY() {
        return seedY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 返回填充颜色的副本（RGBA 格式，至少 4 个分量）。
     * 各分量范围通常为 [0, 1]，但具体取决于画布的色彩空间。
     */
    public float[] getFillColor() {
        return fillColor.clone();
    }

    /** 颜色容差，欧氏距离阈值。值越大，颜色匹配越宽松。非负数。 */
    public float getTolerance() {
        return tolerance;
    }

    /**
     * 参考画布数组（可能为空），用于“所有图层取样”。
     * 若为空，取样仅使用目标画布自身；否则按顺序从参考画布中获取种子颜色。
     * 返回的数组是防御性拷贝，修改不影响原对象。
     */
    public Canvas getReferenceCanvas() {
        return referenceCanvas;
    }

    /**
     * 是否为连续填充。
     * {@code true} 只填充与种子点连通的区域；
     * {@code false} 全局替换所有容差范围内的像素。
     */
    public boolean isContiguous() {
        return contiguous;
    }

    /** 是否启用抗锯齿，在填充边界进行半透明混合。 */
    public boolean isAntiAlias() {
        return antiAlias;
    }

    /** 间隙封闭半径（像素），0 表示禁用。用于自动忽略线稿中的小缝隙。 */
    public float getGapClosingRadius() {
        return gapClosingRadius;
    }

    /**
     * 是否保护透明度。
     * 若为 {@code true}，则只填充原本不透明的像素（Alpha > 0），
     * 完全透明的像素保持不变。
     */
    public boolean isProtectOpacity() {
        return protectOpacity;
    }

    /**
     * 选区遮罩，可能为 {@code null} 表示无选区（全画布有效）。
     * 遮罩中值 > 0 的像素才会参与填充。
     */
    public Mask getMask() {
        return mask;
    }

    /**
     * 混合模式字符串，{@code null} 表示直接替换（默认行为）。
     * 常见值："multiply", "screen", "overlay" 等，具体支持的模式由执行器定义。
     */
    public String getBlendMode() {
        return blendMode;
    }

    /**
     * 填充区域扩展半径（像素），用于覆盖线稿边缘的微小缝隙。
     * 0 表示不扩展。非负数。
     */
    public float getExpandRadius() {
        return expandRadius;
    }

    /**
     * 扩展时是否保护线稿（通常为黑色像素）。
     * 若为 {@code true}，扩展操作将跳过被认为是线稿的像素。
     */
    public boolean isProtectLineArt() {
        return protectLineArt;
    }

    /**
     * 获取线稿检测器，可能为 null。
     * 当 protectLineArt 为 true 且扩展半径 > 0 时，执行器会调用此检测器；
     * 若为 null 则使用默认亮度阈值检测器。
     */
    public LineArtDetector getLineArtDetector() {
        return lineArtDetector;
    }

    /**
     * 获取颜色匹配器，非 null。
     * 默认使用平方欧几里得距离匹配器（{@link ColorMatchers#euclideanSquare()}）。
     */
    public ColorMatcher getColorMatcher() { return colorMatcher; }

    @Override
    public String toString() {
        return "FloodFillRequest{" +
                "targetCanvas=" + targetCanvas +
                ", seed=(" + seedX + "," + seedY + ")" +
                ", fillColor=" + Arrays.toString(fillColor) +
                ", tolerance=" + tolerance +
                ", referenceCanvases=" + referenceCanvas +
                ", contiguous=" + contiguous +
                ", antiAlias=" + antiAlias +
                ", gapClosingRadius=" + gapClosingRadius +
                ", protectOpacity=" + protectOpacity +
                ", mask=" + mask +
                ", blendMode='" + (blendMode == null ? "null (replace)" : blendMode) + '\'' +
                ", expandRadius=" + expandRadius +
                ", protectLineArt=" + protectLineArt +
                '}';
    }

    // ── Builder ──────────────────────────────

    /**
     * 创建一个新的 Builder 实例。
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@link FloodFillRequest} 的 Builder。
     * 所有参数都有合理的默认值，调用 {@link #build()} 时进行校验。
     */
    public static final class Builder {
        private Canvas targetCanvas;
        private double seedX;
        private double seedY;
        private float[] fillColor = {0.0f, 0.0f, 0.0f, 1.0f}; // 默认不透明黑色
        private float tolerance = 0.0f;
        private Canvas referenceCanvas;
        private boolean contiguous = true;
        private boolean antiAlias = true;
        private float gapClosingRadius = 0.0f;
        private boolean protectOpacity = false;
        private Mask mask;
        private String blendMode = null;           // null 表示替换
        private float expandRadius = 0.0f;
        private boolean protectLineArt = false;
        private LineArtDetector lineArtDetector;
        private ColorMatcher colorMatcher;
        private int width = 1;   // 默认值，但应在 build 前设置
        private int height = 1;
        /**
         * 设置目标画布，必填。
         * @param canvas 要填充的图层画布，不能为 null
         */
        public Builder targetCanvas(Canvas canvas) {
            this.targetCanvas = canvas;
            return this;
        }

        /**
         * 设置种子点坐标（整数像素）。
         * @param x X 坐标
         * @param y Y 坐标
         */
        public Builder seed(double x, double y) {
            this.seedX = x;
            this.seedY = y;
            return this;
        }

        /**
         * 设置 RGBA 填充色，传入数组会被复制。
         * 数组长度至少为 4（RGBA）。默认值：不透明黑色 [0,0,0,1]。
         * @param color RGBA 颜色分量，通常范围 0..1
         */
        public Builder fillColor(float[] color) {
            this.fillColor = color.clone();
            return this;
        }

        /**
         * 便捷方法：用四个浮点数设置填充色。
         * @param r 红色分量 (0..1)
         * @param g 绿色分量 (0..1)
         * @param b 蓝色分量 (0..1)
         * @param a Alpha 分量 (0..1)，1 为不透明
         */
        public Builder fillColor(float r, float g, float b, float a) {
            this.fillColor = new float[]{r, g, b, a};
            return this;
        }

        /**
         * 设置颜色容差，非负数，默认 0。
         * @param tolerance 欧氏距离阈值
         */
        public Builder tolerance(float tolerance) {
            this.tolerance = tolerance;
            return this;
        }

        public Builder colorMatcher(ColorMatcher colorMatcher) {
            this.colorMatcher = colorMatcher;
            return this;
        }

        public Builder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        /**
         * 设置参考画布（可变参数）。用于“所有图层取样”时获取种子颜色。
         * 默认无参考画布，仅取样目标画布自身。
         * @param canvas 参考画布，可为 null
         */
        public Builder referenceCanvas(Canvas canvas) {
            this.referenceCanvas = canvas;
            return this;
        }

        /**
         * 是否连续填充，默认 true。
         */
        public Builder contiguous(boolean contiguous) {
            this.contiguous = contiguous;
            return this;
        }

        /**
         * 是否抗锯齿，默认 true。
         */
        public Builder antiAlias(boolean antiAlias) {
            this.antiAlias = antiAlias;
            return this;
        }

        /**
         * 间隙封闭半径，默认 0（禁用）。
         * @param radius 像素数，非负数
         */
        public Builder gapClosingRadius(float radius) {
            this.gapClosingRadius = radius;
            return this;
        }

        /**
         * 是否保护透明度，默认 false。
         * 当为 true 时，不会填充完全透明的像素。
         */
        public Builder protectOpacity(boolean protect) {
            this.protectOpacity = protect;
            return this;
        }

        /**
         * 设置选区遮罩，null 表示无选区，默认 null。
         * @param mask 选区遮罩对象
         */
        public Builder mask(Mask mask) {
            this.mask = mask;
            return this;
        }

        /**
         * 设置混合模式字符串，null 或 "replace" 表示直接替换（默认）。
         * 其他值由执行器解释，如 "multiply", "screen" 等。
         * @param mode 混合模式标识
         */
        public Builder blendMode(String mode) {
            this.blendMode = mode;
            return this;
        }

        /**
         * 填充区域扩展半径，默认 0 不扩展。
         * @param radius 像素数，非负数
         */
        public Builder expandRadius(float radius) {
            this.expandRadius = radius;
            return this;
        }

        /**
         * 扩展时是否保护线稿像素，默认 false。
         * @param protect true 则跳过被认为是线稿的像素（通常为黑色）
         */
        public Builder protectLineArt(boolean protect) {
            this.protectLineArt = protect;
            return this;
        }

        public Builder lineArtDetector(LineArtDetector detector) {
            this.lineArtDetector = detector;
            return this;
        }

        /**
         * 构建不可变的 {@link FloodFillRequest} 实例。
         * 会进行参数合法性校验。
         *
         * @return 填充请求对象
         * @throws NullPointerException 如果 targetCanvas 或 fillColor 为 null
         * @throws IllegalArgumentException 如果 fillColor 长度不足、或数值参数为负数
         */
        public FloodFillRequest build() {
            // 确保 lineArtDetector 非 null（默认值已经保证，但 retain safety）
            if (lineArtDetector == null) {
                lineArtDetector = LineArtDetectors.defaultDetector();
            }
            if (colorMatcher == null) {
                colorMatcher = ColorMatchers.euclideanSquare();
            }
            return new FloodFillRequest(this);
        }
    }
}