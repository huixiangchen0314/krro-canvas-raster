package top.kzre.krro.canvas.raster.bloodfill;

@FunctionalInterface
public interface ColorMatcher {
    /**
     * 比较大数组中的两个像素颜色是否匹配。
     * @param pixels1  像素数组1
     * @param offset1  像素1起始索引
     * @param pixels2  像素数组2
     * @param offset2  像素2起始索引
     * @param channels 通道数
     * @param tolerance 容差
     * @return 是否匹配
     */
    boolean match(float[] pixels1, int offset1,
                  float[] pixels2, int offset2,
                  int channels, float tolerance);

    /** 便捷方法：比较两个独立颜色数组 */
    default boolean match(float[] pixel1, float[] pixel2, float tolerance) {
        return match(pixel1, 0, pixel2, 0, Math.min(pixel1.length, pixel2.length), tolerance);
    }
}