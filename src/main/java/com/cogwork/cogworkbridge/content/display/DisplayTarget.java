package com.cogwork.cogworkbridge.content.display;

/** Server-side endpoint for a display link. */
public interface DisplayTarget {
    void acceptDisplayText(int line, String text);
}
