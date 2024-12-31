package com.example.videoprocessor.service;


import com.example.videoprocessor.Content.FileContent;
import com.example.videoprocessor.pojo.FrameWrapper;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Classname: VideoService
 * Package: com.example.videoprocessor.service
 * Description:
 * 两种思路：
 * 1. 直接将处理视频每一帧。
 * 2. 先将视频分割为图片，处理每一张图片，再将图片合并为视频。
 * @Author: No_Ripple(吴波)
 * @Creat： - 15:58
 * @Version: v1.0
 */
public class VideoService {
    public static void main(String[] args) throws IOException, InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        processVideoToSetMessage(FileContent.VIDEO_PATH_PREFIX + "video.mp4", FileContent.VIDEO_PATH_PREFIX + "new42.mkv", executor, executorService);
//        processVideoToGetMessage(FileContent.VIDEO_PATH_PREFIX + "new4.mkv", executor);
        System.out.println(Duration.between(now, LocalDateTime.now()).toMillis());
    }
    public boolean separateFromFile(String videoPath, String outputDir, int frameRate) throws IOException, InterruptedException {
        File outputDirectory  = new File(outputDir);
        if (!outputDirectory .exists()) outputDirectory.mkdirs();

        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",  // FFmpeg 可执行文件路径
                "-i", videoPath,  // 输入视频文件
                "-threads", "4",  // 线程数
                "-vf", "fps=" + frameRate,  // 设置帧率
                outputDir + "/frame_%04d.png"  // 输出图像文件路径
        );
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);  // 重定向输出流到控制台
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg failed with exit code: " + exitCode);
        }
        System.out.println("Video separation completed successfully.");
        return true;
    }
    public boolean mergeImages(String imagePath, String outputPath, int frameRate) throws IOException, InterruptedException {
        File imageDirectory = new File(imagePath);
        if (!imageDirectory.exists() || !imageDirectory.isDirectory()) {
            throw new IllegalArgumentException("Invalid image directory: " + imagePath);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",  // FFmpeg 可执行文件路径
                "-framerate", String.valueOf(frameRate),  // 设置帧率
                "-i", imagePath + "/frame_%04d.png",  // 输入图像文件路径
                "-c:v", "libx265",  // 使用 H.264 编码器
                "-crf", "23",  // 设置视频质量 (18 是较高质量设置)
                "-preset", "medium",  // 编码速度/压缩比权衡
                "-threads", "4",  // 线程数
                outputPath  // 输出视频文件路径
        );
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);  // 重定向输出流到控制台
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg failed with exit code: " + exitCode);
        }
        System.out.println("Video merge completed successfully.");
        return true;
    }

    public static void processVideoToSetMessage(String inputPath, String outputPath, ExecutorService executor, ExecutorService executorService) throws IOException, InterruptedException {
        FFmpegLogCallback.set();
        // 创建 FFmpegFrameGrabber 实例
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();  // 启动抓取器
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            System.out.println(grabber.grab().image.length);
            // 创建 FFmpegFrameRecorder 实例
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                recorder.setFormat(grabber.getFormat());
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_HUFFYUV);
                System.out.println(grabber.getFrameRate());
                recorder.setFrameRate(60);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_RGB24);
                recorder.setVideoOption("fmt", "rgb24");
//                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // AV_PIX_FMT_YUV420P
                recorder.setFrameNumber(grabber.getLengthInFrames());
//                recorder.setVideoOption("preset", "ultrafast");
//                recorder.setVideoOption("crf", "20");
                recorder.setVideoBitrate(20000000); // 比特率
                recorder.start();  // 启动录制器

                PriorityBlockingQueue<FrameWrapper> processedFrames = new PriorityBlockingQueue<>();
                AtomicInteger completedFrameCount = new AtomicInteger(0);
                AtomicInteger completedFrameCount2 = new AtomicInteger(0);
                executorService.submit(() -> {
                    while (processedFrames.size() < 30) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } // 等待排序
                    while (!processedFrames.isEmpty()) {
                        // 当持续有任务添加到队列时，记录完成的帧数
                        try {
                            Frame frame = processedFrames.take().frame;
                            recorder.record(frame);
                            completedFrameCount2.incrementAndGet();
                        } catch (FFmpegFrameRecorder.Exception | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                // 遍历每一帧
                while (true) {
                    Frame grabbedFrame = grabber.grab();
                    if (grabbedFrame == null) break;

                    Frame localFrame = grabbedFrame.clone(); // 克隆帧以避免并发问题
                    executor.submit(() -> {
                        setMessagePerFrameRGB(localFrame, width, height);
                        processedFrames.put(new FrameWrapper(localFrame));
                        completedFrameCount.incrementAndGet();
                        System.out.println("Processing frame: " + localFrame.timestamp);
                    });
                }
                executor.shutdown();
                executorService.shutdown();
                try {
                    while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        // 持续等待直到所有任务完成
                        System.out.println("waiting for processing");
                    }
                    while (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                        // 持续等待直到所有任务完成
                        System.out.println("waiting for recording");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("任务被中断");
                }
                recorder.stop();recorder.release();
                grabber.stop();grabber.release();
                System.out.println("视频处理完成，共处理了 " + completedFrameCount.get() + " 帧" + "收集了" + completedFrameCount2.get() + " 帧");

            }
        }
    }
    public static void processVideoToGetMessage(String inputPath, ExecutorService executor) throws IOException, InterruptedException {
        FFmpegLogCallback.set();
        // 创建 FFmpegFrameGrabber 实例
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();  // 启动抓取器
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            // 遍历每一帧
            while (true) {
                Frame grabbedFrame = grabber.grab();
                if (grabbedFrame == null) break;
                Frame localFrame = grabbedFrame.clone(); // 克隆帧以避免并发问题
                executor.submit(() -> {
                    getMessagePerFrameRGB(localFrame, width, height);
                    System.out.println("Processing frame: " + localFrame.timestamp);
                });
            }
            executor.shutdown();
            try {
                while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.out.println("waiting for processing");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("任务被中断");
            }
            grabber.stop();
        }
    }
    private static void setMessagePerFrameRGB(Frame frame, int width, int height) {
        // 遍历每个像素并修改其通道数据
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取像素的起始索引
                int index = (y * width + x) * 3;
                // 访问并修改像素的通道值
                ByteBuffer data = (ByteBuffer) frame.image[0];
                if (data.isReadOnly()) System.out.println("read only");
                int red = data.get(index) & 0xFF;
                int green = data.get(index + 1) & 0xFF;
                int blue = data.get(index + 2) & 0xFF;
                // TODO 修改每个通道的最低位为1，可以从 queue 中取出
                red = (red | 1);
                green = (green | 1);
                blue = (blue | 1);
                // 将修改后的值写回ByteBuffer
                data.put(index, (byte) (red & 0xFF));
                data.put(index + 1, (byte) (green & 0xFF));
                data.put(index + 2, (byte) (blue & 0xFF));
            }
        }
    }
    private static void setMessagePerFrameYUV(Frame frame, int width, int height) {
        ByteBuffer yData = (ByteBuffer) frame.image[0];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int yValue = yData.get(index) & 0xFF;
                // TODO: 从队列中取出要嵌入的位
                // 这里我们简单地将LSB设置为1，实际应用中应从队列中取出位
                yValue = (yValue & 0xFE) | 1;  // 设置LSB为1
                yData.put(index, (byte) (yValue & 0xFF));
            }
        }
    }
    private static void getMessagePerFrameYUV(Frame frame, int width, int height) {
        ByteBuffer data = (ByteBuffer) frame.image[0];
        // 遍历每个像素并修改其通道数据
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取像素的起始索引
                int index = y * width + x;
                // 访问像素的通道值
                int yValue = data.get(index) & 0xFF;
                // TODO 获取每个通道的最低位
                int lsb = yValue & 1;
                System.out.println(lsb);
            }
        }
    }
    private static void getMessagePerFrameRGB(Frame frame, int width, int height) {
        // 遍历每个像素并修改其通道数据
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取像素的起始索引
                int index = (y * width + x) * 3;
                // 访问并修改像素的通道值
                ByteBuffer data = (ByteBuffer) frame.image[0];
                int red = data.get(index) & 0xFF;
                int green = data.get(index + 1) & 0xFF;
                int blue = data.get(index + 2) & 0xFF;
                // TODO 获取每个通道的最低位
                red = (red & 1);
                green = (green & 1);
                blue = (blue & 1);
                System.out.println(red + " " + green + " " + blue);
            }
        }
    }




}
