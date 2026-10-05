/*
 * XF FramePack
 * Copyright (c) 2026 xuanfeng0316
 * Licensed under GPL 3.0
 */

package com.xuanfeng.framepack;

import org.jcodec.api.JCodecException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        String input = null;
        String output = null;
        String frameFormat = "png";
        double threshold = 0;
        int throttle = 0;
        boolean chinese = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("-z")) {
                chinese = true;
            } else if (arg.equals("-h") || arg.equals("-help")) {
                PrintOutput.printHelp();
                System.exit(0);
            } else if (arg.equals("-v") || arg.equals("-version")) {
                PrintOutput.printVersion();
                System.exit(0);
            } else if (arg.equals("-threshold")) {
                if (i + 1 >= args.length) {
                    PrintOutput.setChinese(chinese);
                    System.out.println(PrintOutput.invalidArgument());
                    System.exit(1);
                }
                threshold = Double.parseDouble(args[++i]);
            } else if (arg.equals("-frame-format")) {
                if (i + 1 >= args.length) {
                    PrintOutput.setChinese(chinese);
                    System.out.println(PrintOutput.invalidArgument());
                    System.exit(1);
                }
                frameFormat = args[++i];
            } else if (arg.equals("-throttle")) {
                if (i + 1 >= args.length) {
                    PrintOutput.setChinese(chinese);
                    System.out.println(PrintOutput.invalidArgument());
                    System.exit(1);
                }
                throttle = Integer.parseInt(args[++i]);
            } else if (arg.startsWith("-")) {
                PrintOutput.setChinese(chinese);
                System.out.println(PrintOutput.invalidArgument());
                System.exit(1);
            } else {
                if (input == null) {
                    input = arg;
                } else if (output == null) {
                    output = arg;
                } else {
                    PrintOutput.setChinese(chinese);
                    System.out.println(PrintOutput.invalidArgument());
                    System.exit(1);
                }
            }
        }

        PrintOutput.setChinese(chinese);

        if (input == null) {
            System.out.println(PrintOutput.invalidArgument());
            System.exit(1);
        }

        Path inputPath = Paths.get(input);
        if (!Files.exists(inputPath)) {
            System.out.println(PrintOutput.noSuchFile());
            System.exit(1);
        }

        Path outputPath = resolveOutput(inputPath, output);

        if (BFrameDetector.hasBFrames(inputPath)) {
            System.out.println(PrintOutput.bFrameWarning());
            System.out.print(PrintOutput.continuePrompt());
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (answer == null || !answer.trim().equalsIgnoreCase("y")) {
                    System.out.println(PrintOutput.bFrameSuggestion());
                    System.exit(0);
                }
            } catch (IOException e) {
                System.exit(1);
            }
        }

        try {
            FrameExtractor.extract(inputPath, outputPath, frameFormat, threshold, throttle);
        } catch (IOException | JCodecException e) {
            System.out.println(PrintOutput.error());
            System.exit(1);
        }

        System.out.println(PrintOutput.done());
        System.exit(0);
    }

    private static Path resolveOutput(Path inputPath, String output) {
        if (output == null) {
            return Paths.get(inputPath.getFileName().toString() + ".zip");
        }
        if (output.endsWith("/") || output.endsWith("\\")) {
            return Paths.get(output, inputPath.getFileName().toString() + ".zip");
        }
        return Paths.get(output);
    }
}