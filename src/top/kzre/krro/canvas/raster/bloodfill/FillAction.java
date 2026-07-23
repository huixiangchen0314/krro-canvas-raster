package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.tile.Canvas;

@FunctionalInterface
public interface FillAction {
    /**
     * 对已判定可填充的像素执行写入。
     * @param x,y       亚像素坐标（实际像素中心为整数+0.5）
     * @param canvas    可写画布
     * @param fillColor 填充色
     */
    void apply(double x, double y, Canvas canvas, float[] fillColor);
}