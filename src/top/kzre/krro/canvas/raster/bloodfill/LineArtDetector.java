package top.kzre.krro.canvas.raster.bloodfill;

/**
 * 线稿检测器，用于判断某个像素是否属于线稿。
 * 当 {@link FloodFillRequest#isProtectLineArt()} 为 true 且扩展半径 > 0 时，
 * 填充扩展将跳过此检测器判定为 true 的像素。
 */
@FunctionalInterface
public interface LineArtDetector {
    /**
     * 判断给定像素是否为线稿。
     * @param pixel RGBA 颜色分量，长度至少 4，范围通常 0..1
     * @return true 表示该像素为线稿，应被保护；false 则允许填充
     */
    boolean isLineArt(float[] pixel);
}