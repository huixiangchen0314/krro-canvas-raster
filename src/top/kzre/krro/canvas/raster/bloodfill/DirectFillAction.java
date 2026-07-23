package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.tile.Canvas;

final class DirectFillAction implements FillAction {
    static final DirectFillAction INSTANCE = new DirectFillAction();
    @Override
    public void apply(double x, double y, Canvas canvas, float[] fillColor) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        canvas.setPixel(ix, iy, fillColor);
    }
}