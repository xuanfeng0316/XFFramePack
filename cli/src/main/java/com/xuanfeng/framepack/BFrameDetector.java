/*
 * XF FramePack
 * Copyright (c) 2026 xuanfeng0316
 * Licensed under GPL 3.0
 */
 
package com.xuanfeng.framepack;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class BFrameDetector {

    public static boolean hasBFrames(Path input) {
        try (RandomAccessFile file = new RandomAccessFile(input.toFile(), "r")) {
            return findCtts(file);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean findCtts(RandomAccessFile file) throws IOException {
        long length = file.length();
        byte[] buffer = new byte[4];
        for (long pos = 0; pos + 8 <= length; pos++) {
            file.seek(pos);
            file.readFully(buffer);
            if (buffer[0] == 'c' && buffer[1] == 't' && buffer[2] == 't' && buffer[3] == 's') {
                return true;
            }
        }
        return false;
    }
}