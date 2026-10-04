/*
 * XF FramePack
 * Copyright (c) 2026 xuanfeng0316
 * Licensed under GPL 3.0
 */

package com.xuanfeng.framepack;

public class PrintOutput {

    private static boolean chinese = false;

    public static void setChinese(boolean value) {
        chinese = value;
    }

    public static String done() {
        return chinese ? "完成" : "Done";
    }

    public static String error() {
        return chinese ? "错误" : "Error";
    }

    public static String notFound() {
        return chinese ? "未找到" : "Not found";
    }

    public static String noSuchFile() {
        return chinese ? "无此文件或目录" : "No such file or directory";
    }

    public static String invalidArgument() {
        return chinese ? "无效参数" : "Invalid argument";
    }

    public static String processing() {
        return chinese ? "转换" : "Processing";
    }

    public static String output() {
        return chinese ? "输出" : "Output";
    }

    public static String bFrameWarning() {
        return chinese ? "此文件含有B帧，可能会导致帧顺序出现问题" : "This file contains B-frames, frame order may be incorrect";
    }

    public static String continuePrompt() {
        return chinese ? "是否继续？[y/n]：" : "Do you want to continue? [y/n]: ";
    }

    public static String bFrameSuggestion() {
        return chinese ? "建议使用工具去除B帧后再进行转换" : "Suggest removing B-frames before converting";
    }

    public static void printVersion() {
        System.out.println("XF FramePack 0.0.1");
        System.out.println("By xuanfeng0316");
        System.out.println("Copyright (c) 2026 xuanfeng0316");
        System.out.println("Licensed under GPL 3.0");
        System.out.println("https://github.com/xuanfeng0316/XFFramePack");
    }

    public static void printHelp() {
        System.out.println("XF FramePack 0.0.1");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar XFFramePack_0.0.1.jar <input> [output] [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -threshold <value>          default 0, 0 keeps all frames");
        System.out.println("  -frame-format png|jpg|webp  default png");
        System.out.println("  -throttle <ms>              default 0");
        System.out.println("  -z                          chinese output");
        System.out.println("  -h                          show this help");
        System.out.println("  -v, -version                show version");
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "0B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB"};// This have NB,hhh :)
        if (bytes < 1024) {
            return bytes + "B";
        }
        double value = bytes;
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return String.format("%.2f%s", value, units[index]);
    }
}