package app.railguide;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Converts captured RGBA pixels while respecting row padding and pixel stride. */
final class FramePixels {
    static int[] read(ByteBuffer buffer, int width, int height, int rowStride, int pixelStride, int[] reuse) {
        int[] colors = reuse != null && reuse.length == width * height ? reuse : new int[width * height];
        if (pixelStride == 4 && rowStride % 4 == 0) {
            IntBuffer packed = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            for (int y = 0; y < height; y++) {
                packed.position(y * rowStride / 4);
                packed.get(colors, y * width, width);
            }
            for (int i = 0; i < colors.length; i++) {
                int rgba = colors[i];
                colors[i] = 0xff000000 | ((rgba & 255) << 16) | (rgba & 0xff00) | ((rgba >>> 16) & 255);
            }
        } else {
            int start = buffer.position();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int at = start + y * rowStride + x * pixelStride;
                    colors[y * width + x] = 0xff000000 | ((buffer.get(at) & 255) << 16)
                            | ((buffer.get(at + 1) & 255) << 8) | (buffer.get(at + 2) & 255);
                }
            }
        }
        return colors;
    }
}
