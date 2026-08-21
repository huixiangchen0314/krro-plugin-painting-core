package top.kzre.krro.plugin.painting.core.tool;

import top.kzre.krro.util.TiledCanvasUtils;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.HashSet;
import java.util.Set;

public final class Util {
    private Util() {}


    public static Set<Long> transformTiles(
            Set<Long> localDirtyTiles,
            int tileSize,
            float[] mat2d) {

        if (localDirtyTiles == null || localDirtyTiles.isEmpty()) {
            return new HashSet<>();
        }
        if (mat2d == null || mat2d.length < 6) {
            return new HashSet<>(localDirtyTiles);
        }

        Set<Long> result = new HashSet<>();
        for (Long tileKey : localDirtyTiles) {
            int localTX = TiledCanvas.unpackTx(tileKey);
            int localTY = TiledCanvas.unpackTy(tileKey);

            // 局部瓦片的像素范围 → 世界空间四边形（四个角点）
            float x1 = localTX * tileSize;
            float y1 = localTY * tileSize;
            float x2 = x1 + tileSize;
            float y2 = y1 + tileSize;

            float[] p00 = KMath.mat2dTransformPoint(mat2d, x1, y1);
            float[] p10 = KMath.mat2dTransformPoint(mat2d, x2, y1);
            float[] p01 = KMath.mat2dTransformPoint(mat2d, x1, y2);
            float[] p11 = KMath.mat2dTransformPoint(mat2d, x2, y2);

            // 四边形顶点数组（顺时针或逆时针）
            float[][] quad = {p00, p10, p11, p01};

            // AABB 用于快速确定候选世界瓦片范围
            float minX = p00[0], maxX = p00[0], minY = p00[1], maxY = p00[1];
            for (float[] p : quad) {
                if (p[0] < minX) minX = p[0];
                if (p[0] > maxX) maxX = p[0];
                if (p[1] < minY) minY = p[1];
                if (p[1] > maxY) maxY = p[1];
            }

            int minWorldTX = Math.floorDiv((int) Math.floor(minX), tileSize);
            int maxWorldTX = Math.floorDiv((int) Math.ceil(maxX) - 1, tileSize);
            int minWorldTY = Math.floorDiv((int) Math.floor(minY), tileSize);
            int maxWorldTY = Math.floorDiv((int) Math.ceil(maxY) - 1, tileSize);

            // 对每个候选世界瓦片做精确相交测试
            for (int wy = minWorldTY; wy <= maxWorldTY; wy++) {
                for (int wx = minWorldTX; wx <= maxWorldTX; wx++) {
                    float rx = wx * tileSize;
                    float ry = wy * tileSize;
                    if (rectIntersectsQuad(rx, ry, tileSize, quad)) {
                        result.add(TiledCanvas.pack(wx, wy));
                    }
                }
            }
        }
        return result;
    }

    // 点是否在凸四边形内（叉积同号法）
    private static boolean pointInQuad(float px, float py, float[][] quad) {
        int n = quad.length;
        boolean positive = false, negative = false;
        for (int i = 0; i < n; i++) {
            float[] a = quad[i];
            float[] b = quad[(i + 1) % n];
            float cross = (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
            if (cross > 0) positive = true;
            else if (cross < 0) negative = true;
            if (positive && negative) return false; // 符号不一致，外部
        }
        return true;
    }

    // 线段相交检测
    private static boolean segmentsIntersect(float x1, float y1, float x2, float y2,
                                             float x3, float y3, float x4, float y4) {
        float d1 = direction(x3, y3, x4, y4, x1, y1);
        float d2 = direction(x3, y3, x4, y4, x2, y2);
        float d3 = direction(x1, y1, x2, y2, x3, y3);
        float d4 = direction(x1, y1, x2, y2, x4, y4);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        // 共线情况（不考虑）
        return false;
    }

    private static float direction(float x1, float y1, float x2, float y2, float x3, float y3) {
        return (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
    }

    // 轴对齐矩形与凸四边形相交检测
    private static boolean rectIntersectsQuad(float rx, float ry, int tileSize, float[][] quad) {
        float rectLeft = rx;
        float rectRight = rx + tileSize;
        float rectTop = ry + tileSize;   // 假设 y 轴向上，mat2d 中坐标方向与 TiledCanvas 一致
        float rectBottom = ry;

        // 检查矩形角点是否在四边形内
        if (pointInQuad(rectLeft, rectBottom, quad) || pointInQuad(rectLeft, rectTop, quad) ||
                pointInQuad(rectRight, rectBottom, quad) || pointInQuad(rectRight, rectTop, quad)) {
            return true;
        }

        // 检查四边形角点是否在矩形内
        for (float[] p : quad) {
            if (p[0] >= rectLeft && p[0] <= rectRight && p[1] >= rectBottom && p[1] <= rectTop) {
                return true;
            }
        }

        // 检查边相交
        for (int i = 0; i < 4; i++) {
            float[] a = quad[i];
            float[] b = quad[(i + 1) % 4];
            // 与矩形四条边检测
            if (segmentsIntersect(a[0], a[1], b[0], b[1], rectLeft, rectBottom, rectLeft, rectTop) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1], rectLeft, rectTop, rectRight, rectTop) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1], rectRight, rectTop, rectRight, rectBottom) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1], rectRight, rectBottom, rectLeft, rectBottom)) {
                return true;
            }
        }
        return false;
    }
}