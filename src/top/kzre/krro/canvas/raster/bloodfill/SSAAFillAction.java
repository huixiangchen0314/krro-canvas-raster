package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.Canvas;

final class SSAAFillAction implements FillAction {
    private final FillPredicate predicate;
    private final int scale;

    public SSAAFillAction(FillPredicate predicate, int scale) {
        this.predicate = predicate;
        this.scale = scale;
    }

    @Override
    public void apply(double x, double y, Canvas canvas, float[] fillColor) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] original = pool.acquire();
        float[] blended = pool.acquire();
        try {
            canvas.getPixel(ix, iy, original);
            float coverage = computeCoverage(x, y, canvas);
            for (int c = 0; c < channels; c++) {
                blended[c] = original[c] + (fillColor[c] - original[c]) * coverage;
            }
            canvas.setPixel(ix, iy, blended);
        } finally {
            pool.release(blended);
            pool.release(original);
        }
    }

    private float computeCoverage(double x, double y, Canvas canvas) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double step = 1.0 / scale;
        double start = step / 2.0;
        int inside = 0;
        int total = scale * scale;
        for (int sy = 0; sy < scale; sy++) {
            double sampleY = iy + start + sy * step;
            for (int sx = 0; sx < scale; sx++) {
                double sampleX = ix + start + sx * step;
                if (predicate.shouldFill((int) sampleX, (int) sampleY, canvas)) {
                    inside++;
                }
            }
        }
        return (float) inside / total;
    }
}