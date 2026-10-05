/*
 * XF FramePack
 * Copyright (c) 2026 xuanfeng0316
 * Licensed under GPL 3.0
 */

package com.xuanfeng.framepack;

import android.graphics.Bitmap;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FrameExtractor {

    private static final int SAMPLE_WIDTH = 320;
    private static final int SAMPLE_HEIGHT = 180;
    private static final int BLOCKS = 4;

    public interface ProgressListener {
        void onProgress(int current, int total, long bytes);
    }

    public static class Canceller {
        volatile boolean cancelled = false;

        public void cancel() {
            cancelled = true;
        }
    }

    /** 转换后的 ARGB 像素数据 */
    private static class Rgb {
        final int[] pixels;
        final int width;
        final int height;

        Rgb(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    public static void extract(File input, File output, String frameFormat,
                               double threshold, int throttle,
                               ProgressListener listener, Canceller canceller)
            throws IOException, JCodecException {

        SeekableByteChannel channel = null;
        CountingOutputStream counter = null;
        ZipOutputStream zip = null;
        boolean success = false;
        int entries = 0;

        try {
            channel = NIOUtils.readableChannel(input);
            FrameGrab grab = FrameGrab.createFrameGrab(channel);

            // 总帧数仅用于进度显示，有些视频拿不到或不准，所以循环以 getNativeFrame() 返回 null 为准
            int totalFrames = 0;
            try {
                totalFrames = (int) grab.getVideoTrack().getMeta().getTotalFrames();
            } catch (Exception ignored) {
                // 忽略，使用 0 表示未知
            }

            counter = new CountingOutputStream(new FileOutputStream(output));
            zip = new ZipOutputStream(counter);

            int[] baseSample = null;
            int index = 0;

            while (true) {
                if (canceller != null && canceller.cancelled) {
                    break;
                }

                Picture picture = grab.getNativeFrame();
                if (picture == null) {
                    break;
                }
                index++;

                Rgb rgb = toRgb(picture);

                boolean keep;
                if (threshold <= 0) {
                    keep = true;
                } else {
                    int[] sample = toSample(rgb.pixels, rgb.width, rgb.height);
                    if (baseSample == null) {
                        keep = true;
                    } else {
                        keep = difference(baseSample, sample) > threshold;
                    }
                    if (keep) {
                        baseSample = sample;
                    }
                }

                if (keep) {
                    Bitmap bitmap = Bitmap.createBitmap(rgb.pixels, rgb.width, rgb.height,
                            Bitmap.Config.ARGB_8888);
                    try {
                        zip.putNextEntry(new ZipEntry(frameName(index, totalFrames, frameFormat)));
                        Bitmap.CompressFormat fmt = "jpg".equalsIgnoreCase(frameFormat)
                                ? Bitmap.CompressFormat.JPEG
                                : Bitmap.CompressFormat.PNG;
                        if (!bitmap.compress(fmt, 100, zip)) {
                            throw new IOException("Bitmap compress failed at frame " + index);
                        }
                        zip.closeEntry();
                        entries++;
                    } finally {
                        bitmap.recycle();
                    }
                }

                if (listener != null) {
                    listener.onProgress(index, Math.max(totalFrames, index), counter.getCount());
                }

                if (throttle > 0) {
                    try {
                        Thread.sleep(throttle);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 正常收尾：没有任何条目时 ZipOutputStream.close() 会抛异常，直接关底层流
            ZipOutputStream z = zip;
            CountingOutputStream c = counter;
            zip = null;
            counter = null;
            if (entries > 0) {
                z.close();
            } else {
                c.close();
            }

            success = !(canceller != null && canceller.cancelled)
                    && !Thread.currentThread().isInterrupted();
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                }
            }
            if (counter != null) {
                try {
                    counter.close();
                } catch (Exception ignored) {
                }
            }
            NIOUtils.closeQuietly(channel);
            if (!success) {
                // 取消、中断或出错时不留下残缺文件
                output.delete();
            }
        }
    }

    // ------------------------------------------------------------------
    // Picture -> ARGB 转换
    //
    // 注意：jcodec 0.2.x 的样本是「有符号字节、整体偏移 -128」，
    // 即 0..255 的真实值存成 -128..127。所以读取时必须 +128，
    // 而不是 & 0xFF（& 0xFF 会让颜色整体错乱）。
    // ------------------------------------------------------------------

    private static Rgb toRgb(Picture pic) {
        ColorSpace cs = pic.getColor();
        byte[][] planes = pic.getData();

        int fullW = pic.getWidth();
        int fullH = pic.getHeight();

        // 解码器输出通常是 16 对齐的尺寸（如 1088），真实画面由 crop 指定
        int cx = 0, cy = 0, w = fullW, h = fullH;
        Rect crop = pic.getCrop();
        if (crop != null && crop.getWidth() > 0 && crop.getHeight() > 0) {
            cx = clamp(crop.getX(), 0, fullW - 1);
            cy = clamp(crop.getY(), 0, fullH - 1);
            w = Math.min(crop.getWidth(), fullW - cx);
            h = Math.min(crop.getHeight(), fullH - cy);
        }

        int[] out = new int[w * h];
        String csName = String.valueOf(cs);

        if (cs == ColorSpace.RGB || cs == ColorSpace.BGR) {
            boolean bgr = (cs == ColorSpace.BGR);
            byte[] p = planes[0];
            int stride = fullW * 3;
            for (int y = 0; y < h; y++) {
                int rowBase = (y + cy) * stride + cx * 3;
                int dst = y * w;
                for (int x = 0; x < w; x++) {
                    int o = rowBase + x * 3;
                    int c0 = sample(p, o);
                    int c1 = sample(p, o + 1);
                    int c2 = sample(p, o + 2);
                    int r = bgr ? c2 : c0;
                    int b = bgr ? c0 : c2;
                    out[dst + x] = 0xFF000000 | (r << 16) | (c1 << 8) | b;
                }
            }
            return new Rgb(out, w, h);
        }

        byte[] yPlane = planes[0];

        // 只有亮度平面：灰度图
        if (planes.length < 3 || planes[1] == null || planes[2] == null) {
            for (int y = 0; y < h; y++) {
                int rowBase = (y + cy) * fullW + cx;
                int dst = y * w;
                for (int x = 0; x < w; x++) {
                    int v = sample(yPlane, rowBase + x);
                    out[dst + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
                }
            }
            return new Rgb(out, w, h);
        }

        byte[] uPlane = planes[1];
        byte[] vPlane = planes[2];

        // 色度子采样（420 为 1,1；422 为 1,0；444 为 0,0）
        int sw = cs.compWidth[1];
        int sh = cs.compHeight[1];
        int chromaStride = (fullW + (1 << sw) - 1) >> sw;

        // 以 J 结尾的（YUV420J 等）是全范围，其余是 16-235 有限范围
        boolean fullRange = csName.endsWith("J");
        // 没有元数据时按常规做法：高清用 BT.709，其余用 BT.601
        boolean bt709 = (fullW >= 1280 || fullH >= 720);

        int rv, gu, gv, bu;
        if (fullRange) {
            if (bt709) {
                rv = 103206; gu = 12276; gv = 30679; bu = 121609;
            } else {
                rv = 91881; gu = 22554; gv = 46802; bu = 116130;
            }
        } else {
            if (bt709) {
                rv = 117489; gu = 13975; gv = 34925; bu = 138439;
            } else {
                rv = 104597; gu = 25675; gv = 53279; bu = 132201;
            }
        }

        for (int y = 0; y < h; y++) {
            int srcY = y + cy;
            int yRow = srcY * fullW + cx;
            int cRow = (srcY >> sh) * chromaStride;
            int dst = y * w;

            for (int x = 0; x < w; x++) {
                int srcX = x + cx;
                int yy = sample(yPlane, yRow + srcX - cx);
                int u = sample(uPlane, cRow + (srcX >> sw)) - 128;
                int v = sample(vPlane, cRow + (srcX >> sw)) - 128;

                int c = fullRange ? (yy << 16) : (yy - 16) * 76309;

                int r = (c + rv * v + 32768) >> 16;
                int g = (c - gu * u - gv * v + 32768) >> 16;
                int b = (c + bu * u + 32768) >> 16;

                out[dst + x] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
            }
        }
        return new Rgb(out, w, h);
    }

    /** 读取一个样本（还原 -128 偏移），下标越界时取边缘值，绝不抛异常 */
    private static int sample(byte[] a, int i) {
        if (i < 0) {
            i = 0;
        } else if (i >= a.length) {
            i = a.length - 1;
        }
        return a[i] + 128;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ------------------------------------------------------------------
    // 画面差异检测
    // ------------------------------------------------------------------

    private static int[] toSample(int[] src, int srcW, int srcH) {
        int[] result = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
        for (int y = 0; y < SAMPLE_HEIGHT; y++) {
            int sy = y * srcH / SAMPLE_HEIGHT;
            for (int x = 0; x < SAMPLE_WIDTH; x++) {
                int sx = x * srcW / SAMPLE_WIDTH;
                result[y * SAMPLE_WIDTH + x] = src[sy * srcW + sx];
            }
        }
        return result;
    }

    private static double difference(int[] a, int[] b) {
        double global = globalDiff(a, b);
        double block = blockDiff(a, b);
        double hist = histDiff(a, b);
        double edge = edgeDiff(a, b);
        return Math.max(Math.max(global, block), Math.max(hist, edge));
    }

    private static int gray(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static double globalDiff(int[] a, int[] b) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(gray(a[i]) - gray(b[i]));
        }
        return (double) sum / a.length;
    }

    private static double blockDiff(int[] a, int[] b) {
        int bw = SAMPLE_WIDTH / BLOCKS;
        int bh = SAMPLE_HEIGHT / BLOCKS;
        double max = 0;
        for (int by = 0; by < BLOCKS; by++) {
            for (int bx = 0; bx < BLOCKS; bx++) {
                long sum = 0;
                for (int y = 0; y < bh; y++) {
                    for (int x = 0; x < bw; x++) {
                        int idx = (by * bh + y) * SAMPLE_WIDTH + (bx * bw + x);
                        sum += Math.abs(gray(a[idx]) - gray(b[idx]));
                    }
                }
                double avg = (double) sum / (bw * bh);
                if (avg > max) {
                    max = avg;
                }
            }
        }
        return max;
    }

    private static double histDiff(int[] a, int[] b) {
        int[] ha = histogram(a);
        int[] hb = histogram(b);
        long sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += Math.abs(ha[i] - hb[i]);
        }
        return (double) sum / (2 * a.length) * 255;
    }

    private static int[] histogram(int[] pixels) {
        int[] hist = new int[256];
        for (int p : pixels) {
            hist[gray(p)]++;
        }
        return hist;
    }

    private static double edgeDiff(int[] a, int[] b) {
        return Math.abs(edgeSum(a) - edgeSum(b));
    }

    private static double edgeSum(int[] pixels) {
        long sum = 0;
        for (int y = 1; y < SAMPLE_HEIGHT - 1; y++) {
            for (int x = 1; x < SAMPLE_WIDTH - 1; x++) {
                int idx = y * SAMPLE_WIDTH + x;
                int c = gray(pixels[idx]);
                int r = gray(pixels[idx + 1]);
                int d = gray(pixels[idx + SAMPLE_WIDTH]);
                sum += Math.abs(c - r) + Math.abs(c - d);
            }
        }
        return (double) sum / (SAMPLE_WIDTH * SAMPLE_HEIGHT);
    }

    private static String frameName(int index, int totalFrames, String format) {
        int digits = totalFrames > 0 ? String.valueOf(totalFrames).length() : 6;
        return String.format("%0" + digits + "d.%s", index, format);
    }

    static class CountingOutputStream extends FilterOutputStream {
        private long count = 0;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        long getCount() {
            return count;
        }
    }
}