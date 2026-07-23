package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;

/**
 * 线稿保护装饰器：如果目标像素被检测为线稿，则跳过写入。
 * 否则委托给包装的 {@link FillAction} 执行。
 */
final class LineArtProtectAction implements FillAction {
    private final FillAction inner;
    private final LineArtDetector detector;

    LineArtProtectAction(FillAction inner, LineArtDetector detector) {
        this.inner = inner;
        this.detector = detector;
    }

    @Override
    public void apply(double x, double y, Canvas canvas, float[] fillColor) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] pixel = pool.acquire();
        try {
            canvas.getPixel(ix, iy, pixel);
            if (!detector.isLineArt(pixel)) {
                inner.apply(x, y, canvas, fillColor);
            }
            // 如果是线稿，则什么也不做（保护线稿）
        } finally {
            pool.release(pixel);
        }
    }
}