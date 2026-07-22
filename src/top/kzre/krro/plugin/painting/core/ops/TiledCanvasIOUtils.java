package top.kzre.krro.plugin.painting.core.ops;


import top.kzre.krro.util.tile.RLE;
import top.kzre.krro.util.tile.TiledCanvas;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public final class TiledCanvasIOUtils {

    private TiledCanvasIOUtils() {}

    /**
     * 将瓦片映射写入输出流。如果 dirtyTiles 非空，则仅写入脏瓦片。
     * 每个瓦片数据采用游程编码（RLE）压缩存储。
     */
    public static void writeTiles(Map<Long, float[]> tiles,
                                  Collection<Long> dirtyTiles,
                                  OutputStream out) throws IOException {
        Objects.requireNonNull(tiles, "tiles");
        Objects.requireNonNull(out, "out");

        Set<Long> keysToWrite;
        if (dirtyTiles != null && !dirtyTiles.isEmpty()) {
            keysToWrite = new HashSet<>(dirtyTiles);
            keysToWrite.retainAll(tiles.keySet());
        } else {
            keysToWrite = new HashSet<>(tiles.keySet());
        }

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(keysToWrite.size());
        for (long key : keysToWrite) {
            float[] data = tiles.get(key);
            dos.writeLong(key);
            RLE.writeRLE(data, dos);
        }
        dos.flush();
    }

    /**
     * 从输入流读取所有瓦片数据，重建瓦片映射。
     */
    public static Map<Long, float[]> readTiles(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        DataInputStream dis = new DataInputStream(in);
        int count = dis.readInt();
        Map<Long, float[]> tiles = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            long key = dis.readLong();
            float[] data = RLE.readRLE(dis);
            tiles.put(key, data);
        }
        return tiles;
    }

    // ── TiledCanvas 整体读写（支持全量 / 脏瓦片）────────────

    /**
     * 将 TiledCanvas 的全部瓦片写入输出流。
     */
    public static void writeTiledCanvas(TiledCanvas canvas, OutputStream out) throws IOException {
        writeTiledCanvas(canvas, null, out);
    }

    /**
     * 将 TiledCanvas 的指定脏瓦片（或全部瓦片）写入输出流。
     * @param dirtyTiles 脏瓦片的 pack 键集合，若为 null 或空则写入所有瓦片
     */
    public static void writeTiledCanvas(TiledCanvas canvas, Collection<Long> dirtyTiles, OutputStream out) throws IOException {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(out, "out");
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(canvas.getTileSize());
        float[] defaultPixel = canvas.getDefaultPixel();
        for (float v : defaultPixel) {
            dos.writeFloat(v);
        }
        Map<Long, float[]> tiles = new HashMap<>();
        canvas.readTiles(tiles::putAll);
        writeTiles(tiles, dirtyTiles, dos);
        dos.flush();
    }

    /**
     * 从输入流读取完整的 TiledCanvas。
     */
    public static TiledCanvas readTiledCanvas(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        DataInputStream dis = new DataInputStream(in);
        int tileSize = dis.readInt();
        float[] defaultPixel = new float[4];
        for (int i = 0; i < 4; i++) {
            defaultPixel[i] = dis.readFloat();
        }
        TiledCanvas canvas = new TiledCanvas(tileSize, defaultPixel);
        Map<Long, float[]> tiles = readTiles(dis);
        canvas.mergeTiles(tiles);
        return canvas;
    }


    /**
     * 将 TiledCanvas 全部写入文件。
     */
    public static void writeTiledCanvasToFile(TiledCanvas canvas, String filePath) throws IOException {
        writeTiledCanvasToFile(canvas, filePath, null);
    }

    /**
     * 将 TiledCanvas 的指定脏瓦片写入文件。
     */
    public static void writeTiledCanvasToFile(TiledCanvas canvas, String filePath, Collection<Long> dirtyTiles) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            writeTiledCanvas(canvas, dirtyTiles, out);
        }
    }



    /**
     * 从指定文件读取 TiledCanvas。
     */
    public static TiledCanvas readTiledCanvasFromFile(String filePath) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(filePath)))) {
            return readTiledCanvas(in);
        }
    }
}