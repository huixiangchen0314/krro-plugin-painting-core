package top.kzre.krro.plugin.painting.canvas;

import javafx.scene.image.PixelWriter;

public class ViewportUploader {
    public static void upload(float[] src, int srcW, int srcH,
                              double offsetX, double offsetY, double zoom,
                              PixelWriter writer, int canvasW, int canvasH) {
        for (int sy = 0; sy < canvasH; sy++) {
            for (int sx = 0; sx < canvasW; sx++) {
                double lx = sx / zoom + offsetX;
                double ly = sy / zoom + offsetY;
                if (lx < 0 || lx >= srcW || ly < 0 || ly >= srcH) {
                    writer.setArgb(sx, sy, 0);
                    continue;
                }
                int x0 = (int) Math.floor(lx);
                int y0 = (int) Math.floor(ly);
                double fx = lx - x0;
                double fy = ly - y0;
                int x1 = Math.min(x0 + 1, srcW - 1);
                int y1 = Math.min(y0 + 1, srcH - 1);
                // 获取四个像素的原始 RGBA 值
                int idx00 = (y0 * srcW + x0) * 4;
                int idx10 = (y0 * srcW + x1) * 4;
                int idx01 = (y1 * srcW + x0) * 4;
                int idx11 = (y1 * srcW + x1) * 4;

                float r = lerp(lerp(src[idx00], src[idx10], fx), lerp(src[idx01], src[idx11], fx), fy);
                float g = lerp(lerp(src[idx00+1], src[idx10+1], fx), lerp(src[idx01+1], src[idx11+1], fx), fy);
                float b = lerp(lerp(src[idx00+2], src[idx10+2], fx), lerp(src[idx01+2], src[idx11+2], fx), fy);
                float a = lerp(lerp(src[idx00+3], src[idx10+3], fx), lerp(src[idx01+3], src[idx11+3], fx), fy);

                int alpha = Math.min(255, Math.max(0, (int)(a * 255)));
                int rPre = Math.min(255, Math.max(0, (int)(r * a * 255)));
                int gPre = Math.min(255, Math.max(0, (int)(g * a * 255)));
                int bPre = Math.min(255, Math.max(0, (int)(b * a * 255)));
                int argb = (alpha << 24) | (rPre << 16) | (gPre << 8) | bPre;
                writer.setArgb(sx, sy, argb);
            }
        }
    }

    private static float lerp(float a, float b, double t) {
        return (float)(a + (b - a) * t);
    }
}