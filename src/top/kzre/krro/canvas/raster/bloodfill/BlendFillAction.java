package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;

/**
 * 混合模式装饰器：读取当前像素，与填充色按模式混合，然后将混合结果委托给包装的动作。
 * 若混合模式为 null，则直接传递原始填充色。
 */
final class BlendFillAction implements FillAction {
    private final FillAction inner;
    private final String blendMode;

    BlendFillAction(FillAction inner, String blendMode) {
        this.inner = inner;
        this.blendMode = blendMode;
    }

    @Override
    public void apply(double x, double y, Canvas canvas, float[] fillColor) {
        if (blendMode == null) {
            inner.apply(x, y, canvas, fillColor);
            return;
        }
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] base = pool.acquire();
        try {
            canvas.getPixel(ix, iy, base);
            float[] result = Blends.blendWithAlpha(blendMode, base, fillColor);
            inner.apply(x, y, canvas, result);
        } finally {
            pool.release(base);
        }
    }
}