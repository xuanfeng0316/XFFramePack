# XF FramePack

Extract every frame from a video and pack them.

## Usage

java -jar XFFramePack_0.0.1.jar <input> [output] [options]

Extract every frame from a video, name them by the original frame number, zero-padded to the digit count of the total frame count, and pack them into a zip.

input    video file path, required
output   optional. Defaults to the current directory when omitted. Treated as a directory if it ends with / or \, otherwise as a full file path

-threshold <value>           default 0, 0 keeps all frames, otherwise keeps frames whose difference from the previous kept frame exceeds the threshold, frame numbers are the original ones, gaps mean omitted frames
-frame-format png|jpg|webp   default png
-throttle <ms>               default 0, interval between frames
-z                           chinese output
-h                           show help
-v                           show version

## Examples

java -jar XFFramePack_0.0.1.jar video.mp4
java -jar XFFramePack_0.0.1.jar video.mp4 out/result.zip

## Notes

The output zip will be very large. Every frame is a separate image, and PNG barely compresses, so the zip size is close to the sum of all frame images. A few minutes of 1080p video, fully kept, can be tens of GB. Use -threshold, or switch to -frame-format jpg to reduce size.

If the video contains B-frames, the frame order may be incorrect. The program detects this and asks whether to continue. It is recommended to remove B-frames before converting.

Currently only MP4 is supported.

## Build

mvn clean package

## Third-party libraries

This program uses third-party libraries, see [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).
