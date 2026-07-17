package top.kzre.krro.plugin.painting.core.ops;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * 瓦片画布的 IO 工具类，仅负责 tiles (Map<Long, float[]>) 的读写。
 * 元数据（画布尺寸、瓦片大小等）由调用方自行管理。
 */
public final class TiledCanvasIOUtils {

    private TiledCanvasIOUtils() {}

    /**
     * 将瓦片映射写入输出流。如果 dirtyTiles 非空，则仅写入脏瓦片。
     *
     * @param tiles      完整的瓦片映射（键为打包的 tile 坐标，值为 RGBA 浮点数组）
     * @param dirtyTiles 脏瓦片键集合，为 null 或空时写入全部瓦片
     * @param out        目标输出流
     * @throws IOException 写入出错时抛出
     */
    public static void writeTiles(Map<Long, float[]> tiles, Set<Long> dirtyTiles, OutputStream out) throws IOException {
        Objects.requireNonNull(tiles, "tiles");
        Objects.requireNonNull(out, "out");

        // 确定实际要写入的瓦片键
        Set<Long> keysToWrite;
        if (dirtyTiles != null && !dirtyTiles.isEmpty()) {
            keysToWrite = new HashSet<>(dirtyTiles);
            keysToWrite.retainAll(tiles.keySet());          // 只保留实际存在的瓦片
        } else {
            keysToWrite = new HashSet<>(tiles.keySet());
        }

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(keysToWrite.size());                // 写入瓦片数量
        for (long key : keysToWrite) {
            float[] data = tiles.get(key);
            dos.writeLong(key);                         // tile 键
            dos.writeInt(data.length);                  // 数组长度（元素个数）
            for (float v : data) {
                dos.writeFloat(v);                      // 每个 float (4 bytes, big‑endian)
            }
        }
        dos.flush();
    }

    /**
     * 从输入流读取所有瓦片数据，重建瓦片映射。
     *
     * @param in 输入流（必须由 writeTiles 写入，或格式兼容）
     * @return 包含所有读取到的瓦片的 HashMap
     * @throws IOException 读取出错时抛出
     */
    public static Map<Long, float[]> readTiles(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        DataInputStream dis = new DataInputStream(in);
        int count = dis.readInt();                      // 瓦片数量
        Map<Long, float[]> tiles = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            long key = dis.readLong();
            int len = dis.readInt();
            float[] data = new float[len];
            for (int j = 0; j < len; j++) {
                data[j] = dis.readFloat();
            }
            tiles.put(key, data);
        }
        return tiles;
    }

    // ── 文件便捷方法 ────────────────────────────────────

    /** 将瓦片写入文件（脏模式） */
    public static void writeTilesToFile(Map<Long, float[]> tiles, Set<Long> dirtyTiles, File file) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
            writeTiles(tiles, dirtyTiles, out);
        }
    }

    /** 从文件读取所有瓦片 */
    public static Map<Long, float[]> readTilesFromFile(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            return readTiles(in);
        }
    }
}