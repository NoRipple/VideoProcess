package com.example.videoprocessor.pojo;

import org.bytedeco.javacv.Frame;

import java.awt.*;

/**
 * Classname: Element
 * Package: com.example.videoprocessor.pojo
 * Description:
 *
 * @Author: No_Ripple(吴波)
 * @Creat： - 11:07
 * @Version: v1.0
 */
public class Element implements Comparable<Element> {
    public Frame frame;

    public Element(Frame frame) {
        this.frame = frame;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(Element element) {
        if (element == null || element.frame == null || this.frame == null) {
            throw new NullPointerException("Frame or element cannot be null");
        }
        return Long.compare(this.frame.timestamp, element.frame.timestamp);
    }
}
