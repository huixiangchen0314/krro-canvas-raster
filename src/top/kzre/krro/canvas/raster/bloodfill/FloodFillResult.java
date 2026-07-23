package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.tile.Canvas;

import java.util.Collections;
import java.util.Set;

/**
 * 洪水填充操作的结果，不可变。
 * 包含修改后的画布、受影响的瓦片键集合以及是否实际发生改变。
 */
public final class FloodFillResult {

    private final Canvas canvas;
    private final Set<Long> dirtyTiles;
    private final boolean changed;

    /**
     * 构造一个结果实例。
     *
     * @param canvas     填充后的画布（可能与原画布相同，若未发生改变）
     * @param dirtyTiles 受影响的瓦片键集合（只读），为空表示无瓦片变更
     * @param changed    是否实际填充了任何像素
     */
    public FloodFillResult(Canvas canvas, Set<Long> dirtyTiles, boolean changed) {
        this.canvas = canvas;
        this.dirtyTiles = dirtyTiles == null ? Collections.emptySet() : Collections.unmodifiableSet(dirtyTiles);
        this.changed = changed;
    }

    /** 返回填充后的画布。如果未发生任何变化，可能是传入的原始画布。 */
    public Canvas getCanvas() {
        return canvas;
    }

    /** 返回受影响的瓦片键集合，只读，可能为空。 */
    public Set<Long> getDirtyTiles() {
        return dirtyTiles;
    }

    /** 如果填充操作实际修改了任何像素，返回 {@code true}。 */
    public boolean isChanged() {
        return changed;
    }

    @Override
    public String toString() {
        return "FloodFillResult{" +
                "canvas=" + canvas +
                ", dirtyTiles=" + dirtyTiles.size() +
                ", changed=" + changed +
                '}';
    }
}