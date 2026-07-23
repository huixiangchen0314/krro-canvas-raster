package top.kzre.krro.canvas.raster.bloodfill;

/**
 * {@link FillAction} 的静态工厂类，提供常用的填充动作以及装饰器组合。
 * <p>
 * 所有动作均无共享可变状态，可安全用于多线程环境。
 * 装饰器可嵌套使用，典型的组合顺序为：
 * <pre>{@code
 * FillAction action = FillActions.protectLineArt(
 *     FillActions.blend("multiply", FillActions.direct()),
 *     LineArtDetectors.defaultDetector()
 * );
 * }</pre>
 * 注意：SSAA 抗锯齿动作是一个终端动作，直接写入画布，因此不适合被其它需要委托内部的动作装饰。
 */
public final class FillActions {

    private FillActions() {}

    /**
     * 返回一个直接替换颜色的终端动作。
     * 该动作直接将传入的填充色写入指定像素，不读取原像素，不进行任何混合。
     */
    public static FillAction direct() {
        return DirectFillAction.INSTANCE;
    }

    /**
     * 创建一个超采样抗锯齿（SSAA）终端动作。
     * <p>
     * 该动作会在指定像素内部以 {@code scale × scale} 的子像素网格进行采样，
     * 使用给定的 {@link FillPredicate} 判断每个子样本点是否属于填充区域，
     * 统计覆盖率，然后将填充色与原像素按覆盖率线性插值后直接写入画布。
     * <p>
     * 由于该动作直接写入画布，不会委托其它动作，因此不适合被需要委托内部的动作（如混合模式）包裹。
     *
     * @param predicate 用于判断子像素是否可被填充的谓词
     * @param scale     超采样因子（≥1），值越大抗锯齿质量越高，但性能开销也越大
     * @return SSAA 抗锯齿动作
     */
    public static FillAction ssaa(FillPredicate predicate, int scale) {
        return new SSAAFillAction(predicate, scale);
    }

    /**
     * 便捷方法：创建 2×2 超采样抗锯齿动作。
     * 等价于 {@code ssaa(predicate, 2)}。
     *
     * @param predicate 填充判定谓词
     * @return 2×2 SSAA 动作
     */
    public static FillAction ssaa2x2(FillPredicate predicate) {
        return new SSAAFillAction(predicate, 2);
    }

    /**
     * 创建一个混合模式装饰器。
     * <p>
     * 先读取指定像素的当前颜色，与传入的填充色按指定的混合模式进行计算，
     * 然后将计算得到的新颜色委托给 {@code inner} 动作执行写入。
     * 若 {@code blendMode} 为 {@code null}，则直接将原始填充色传递给 {@code inner}，不进行混合。
     *
     * @param blendMode 混合模式字符串，如 {@code "multiply"}, {@code "screen"} 等，{@code null} 表示替换
     * @param inner     实际负责写入的动作
     * @return 装饰后的动作
     */
    public static FillAction blend(FillAction inner, String blendMode) {
        return new BlendFillAction(inner, blendMode);
    }

    /**
     * 创建一个线稿保护装饰器。
     * <p>
     * 在调用内部动作前，先读取目标像素的颜色，并通过 {@code detector} 判断是否为线稿。
     * 若为线稿则跳过写入（无操作）；否则委托给 {@code inner} 执行正常填充。
     *
     * @param inner    实际执行填充的动作
     * @param detector 线稿检测器
     * @return 装饰后的动作
     */
    public static FillAction protectLineArt(FillAction inner, LineArtDetector detector) {
        return new LineArtProtectAction(inner, detector);
    }


}