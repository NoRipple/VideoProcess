package com.example.videoprocessor.pojo;

import org.bytedeco.javacv.Frame;

/**
 * Classname: FrameWrapper
 * Package: com.example.videoprocessor.pojo
 * Description:
 * 包装 Frame 为了实现优先队列根据 timestamp 的自排序
 * @Author: No_Ripple(吴波)
 * @Creat： - 11:07
 * @Version: v1.0
 */
public class FrameWrapper implements Comparable<FrameWrapper> {
    public Frame frame;

    public FrameWrapper(Frame frame) {
        this.frame = frame;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(FrameWrapper element) {
        if (element == null || element.frame == null || this.frame == null) {
            throw new NullPointerException("Frame or element cannot be null");
        }
        return Long.compare(this.frame.timestamp, element.frame.timestamp);
    }
}
