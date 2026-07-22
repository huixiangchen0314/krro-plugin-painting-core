package top.kzre.krro.plugin.painting.core.tool;

import top.kzre.krro.util.TiledCanvasUtils;

import java.util.HashSet;
import java.util.Set;

public final class Util {
    private Util() {}

    /**
     * 将图层局部脏瓦片转换为世界坐标脏瓦片。
     *
     * @param localDirtyTiles 局部脏瓦片键集合（可能为 null 或空）
     * @param tileSize        瓦片尺寸（像素）
     * @param layerX          图层 X 平移（像素）
     * @param layerY          图层 Y 平移（像素）
     * @param scaleX          图层水平缩放，1.0 表示无缩放
     * @param scaleY          图层垂直缩放，1.0 表示无缩放
     * @param rotation        图层旋转角度（弧度）
     * @return 世界坐标脏瓦片集合；若变换奇异（如缩放为 0）返回 null，表示需全图刷新
     */
    public static Set<Long> localToWorldDirtyTiles(
            Set<Long> localDirtyTiles, int tileSize,
            double layerX, double layerY,
            double scaleX, double scaleY, double rotation) {

        if (localDirtyTiles == null || localDirtyTiles.isEmpty()) {
            return new HashSet<>();
        }

        // 避免奇异变换
        if (Math.abs(scaleX) < 1e-10 || Math.abs(scaleY) < 1e-10) {
            return null;
        }

        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double a = scaleX * cos;
        double b = scaleX * sin;
        double c = -scaleY * sin;
        double d = scaleY * cos;

        Set<Long> worldTiles = new HashSet<>();
        for (long key : localDirtyTiles) {
            int tx = TiledCanvasUtils.unpackTx(key);
            int ty = TiledCanvasUtils.unpackTy(key);

            double x0 = tx * tileSize;
            double y0 = ty * tileSize;
            double x1 = x0 + tileSize;
            double y1 = y0 + tileSize;

            // 四个角点变换
            double wx00 = a * x0 + c * y0 + layerX;
            double wy00 = b * x0 + d * y0 + layerY;
            double wx01 = a * x0 + c * y1 + layerX;
            double wy01 = b * x0 + d * y1 + layerY;
            double wx10 = a * x1 + c * y0 + layerX;
            double wy10 = b * x1 + d * y0 + layerY;
            double wx11 = a * x1 + c * y1 + layerX;
            double wy11 = b * x1 + d * y1 + layerY;

            double minWx = Math.min(Math.min(wx00, wx01), Math.min(wx10, wx11));
            double maxWx = Math.max(Math.max(wx00, wx01), Math.max(wx10, wx11));
            double minWy = Math.min(Math.min(wy00, wy01), Math.min(wy10, wy11));
            double maxWy = Math.max(Math.max(wy00, wy01), Math.max(wy10, wy11));

            int minTx = TiledCanvasUtils.tileX((int) Math.floor(minWx), tileSize);
            int maxTx = TiledCanvasUtils.tileX((int) Math.floor(maxWx), tileSize);
            int minTy = TiledCanvasUtils.tileY((int) Math.floor(minWy), tileSize);
            int maxTy = TiledCanvasUtils.tileY((int) Math.floor(maxWy), tileSize);

            for (int y = minTy; y <= maxTy; y++) {
                for (int x = minTx; x <= maxTx; x++) {
                    worldTiles.add(TiledCanvasUtils.pack(x, y));
                }
            }
        }
        return worldTiles;
    }
}