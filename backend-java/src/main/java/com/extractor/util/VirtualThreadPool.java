package com.extractor.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadPool {
    
    /**
     * Creates a Virtual Thread executor with named threads for better debugging.
     * 
     * @param prefix The prefix for the thread name (e.g., "video-pointer-", "audio-worker-")
     */
    public static ExecutorService createNamedVirtualExecutor(String prefix) {
        ThreadFactory factory = Thread.ofVirtual()
                .name(prefix, 1) // e.g., video-pointer-1, video-pointer-2
                .factory();
                
        return Executors.newThreadPerTaskExecutor(factory);
    }
}