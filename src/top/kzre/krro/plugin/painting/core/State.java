package top.kzre.krro.plugin.painting.core;

import top.kzre.krro.util.tile.TiledCanvas;

import java.util.Arrays;
import java.util.Set;

public class State {

    /**
     * 清空目标数组中指定脏瓦片区域的像素（设为透明黑色）。
     *
     * @param dest       目标 RGBA 浮点数组 (width * height * 4)
     * @param width      画布宽度（像素）
     * @param height     画布高度（像素）
     * @param dirtyTiles 脏瓦片键集合，键为 pack(tx, ty) 格式
     * @param tileSize   瓦片边长（像素）
     */
    public static void clearDirtyTiles(float[] dest, int width, int height,
                                       Set<Long> dirtyTiles, int tileSize) {
        if (dest == null || dirtyTiles == null || dirtyTiles.isEmpty()) return;

        for (long key : dirtyTiles) {
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);
            int startX = Math.max(0, tx * tileSize);
            int startY = Math.max(0, ty * tileSize);
            int endX = Math.min(startX + tileSize, width);
            int endY = Math.min(startY + tileSize, height);

            if (startX >= endX || startY >= endY) continue;

            for (int y = startY; y < endY; y++) {
                int fromIdx = (y * width + startX) * 4;
                int toIdx = fromIdx + (endX - startX) * 4;
                Arrays.fill(dest, fromIdx, toIdx, 0.0f);
            }
        }
    }
}