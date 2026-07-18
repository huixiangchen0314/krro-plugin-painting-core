package top.kzre.krro.plugin.painting.core;

public final class Viewport {

    /**
     * 根据屏幕坐标和视口参数采样源像素。
     *
     * @param src     像素数据，长度为 srcW * srcH * 4，每个分量 0~1
     * @param srcW    源图像宽度
     * @param srcH    源图像高度
     * @param sx      屏幕上的 x 坐标（像素）
     * @param sy      屏幕上的 y 坐标（像素）
     * @param offsetX 视口左上角在逻辑空间中的 X 偏移
     * @param offsetY 视口左上角在逻辑空间中的 Y 偏移
     * @param zoom    缩放比例
     * @return 预乘 Alpha 的 ARGB int
     */
    public static int samplePixel(float[] src, int srcW, int srcH,
                                  int sx, int sy,
                                  double offsetX, double offsetY, double zoom) {
        double lx = sx / zoom + offsetX;
        double ly = sy / zoom + offsetY;
        return samplePixel(src, srcW, srcH, lx, ly);
    }

    /**
     * 根据源图像（float RGBA 0~1）和连续坐标采样，返回预乘 Alpha 的 ARGB 整数。
     *
     * @param src  像素数据，长度为 srcW * srcH * 4，每个分量 0~1
     * @param srcW 源图像宽度
     * @param srcH 源图像高度
     * @param lx   源图像上的 x 坐标（允许小数）
     * @param ly   源图像上的 y 坐标（允许小数）
     * @return 预乘 Alpha 的 ARGB int（0x00000000 表示全透明）
     */
    public static int samplePixel(float[] src, int srcW, int srcH, double lx, double ly) {
        // 边界检查
        if (lx < 0 || lx >= srcW || ly < 0 || ly >= srcH) {
            return 0;
        }

        int x0 = (int) Math.floor(lx);
        int y0 = (int) Math.floor(ly);
        double fx = lx - x0;
        double fy = ly - y0;
        int x1 = Math.min(x0 + 1, srcW - 1);
        int y1 = Math.min(y0 + 1, srcH - 1);

        // 四个像素的起始索引
        int idx00 = (y0 * srcW + x0) * 4;
        int idx10 = (y0 * srcW + x1) * 4;
        int idx01 = (y1 * srcW + x0) * 4;
        int idx11 = (y1 * srcW + x1) * 4;

        // 双线性插值
        float r = lerp(lerp(src[idx00],     src[idx10],     fx),
                lerp(src[idx01],     src[idx11],     fx), fy);
        float g = lerp(lerp(src[idx00 + 1], src[idx10 + 1], fx),
                lerp(src[idx01 + 1], src[idx11 + 1], fx), fy);
        float b = lerp(lerp(src[idx00 + 2], src[idx10 + 2], fx),
                lerp(src[idx01 + 2], src[idx11 + 2], fx), fy);
        float a = lerp(lerp(src[idx00 + 3], src[idx10 + 3], fx),
                lerp(src[idx01 + 3], src[idx11 + 3], fx), fy);

        // 预乘 Alpha 并转为 0~255 整数
        int alpha = clamp255((int) (a * 255));
        int rPre  = clamp255((int) (r * a * 255));
        int gPre  = clamp255((int) (g * a * 255));
        int bPre  = clamp255((int) (b * a * 255));

        return (alpha << 24) | (rPre << 16) | (gPre << 8) | bPre;
    }

    private static float lerp(float a, float b, double t) {
        return (float) (a + (b - a) * t);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}