package top.kzre.krro.plugin.painting.core.ops;

import top.kzre.krro.canvas.core.Arrays;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public final class TiledCanvasIOUtils {

    private TiledCanvasIOUtils() {}

    /**
     * 将瓦片映射写入输出流。如果 dirtyTiles 非空，则仅写入脏瓦片。
     * 每个瓦片数据采用游程编码（RLE）压缩存储。
     */
    public static void writeTiles(Map<Long, float[]> tiles, Collection<Long> dirtyTiles, OutputStream out) throws IOException {
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
        dos.writeInt(keysToWrite.size());                // 瓦片数量
        for (long key : keysToWrite) {
            float[] data = tiles.get(key);
            dos.writeLong(key);                         // tile 键
            Arrays.writeRLE(data, dos);                 // RLE 压缩数据
        }
        dos.flush();
    }

    /**
     * 从输入流读取所有瓦片数据，重建瓦片映射。
     * 每个瓦片数据使用游程编码（RLE）解压还原。
     */
    public static Map<Long, float[]> readTiles(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        DataInputStream dis = new DataInputStream(in);
        int count = dis.readInt();                      // 瓦片数量
        Map<Long, float[]> tiles = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            long key = dis.readLong();
            float[] data = Arrays.readRLE(dis);         // RLE 解压
            tiles.put(key, data);
        }
        return tiles;
    }

    // ── 文件便捷方法 ────────────────────────────────────

    public static void writeTilesToFile(Map<Long, float[]> tiles, Set<Long> dirtyTiles, File file) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
            writeTiles(tiles, dirtyTiles, out);
        }
    }

    public static Map<Long, float[]> readTilesFromFile(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return readTiles(in);
        }
    }
}