package io.sdkman.util;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一线程管理器
 * 使用线程池替代直接创建Thread，提供更好的资源管理和性能
 */
public class ThreadManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);

    // 单例实例
    private static volatile ThreadManager instance;

    // 后台任务线程池 - 用于执行I/O密集型任务（CLI命令、网络请求等）
    private final ExecutorService backgroundExecutor;

    // UI更新线程池 - 用于执行轻量级的UI相关任务
    private final ExecutorService uiExecutor;

    private ThreadManager() {
        // 创建有界的后台线程池，防止资源耗尽
        backgroundExecutor = Executors.newFixedThreadPool(
            4,  // 最多4个并发CLI任务
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "SDKMAN-Background-" + threadNumber.getAndIncrement());
                }
            }
        );

        // 创建缓存的UI线程池，用于轻量级任务
        uiExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SDKMAN-UI-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        logger.info("ThreadManager initialized with background pool size: 4");
    }

    /**
     * 获取单例实例
     */
    public static ThreadManager getInstance() {
        if (instance == null) {
            synchronized (ThreadManager.class) {
                if (instance == null) {
                    instance = new ThreadManager();
                }
            }
        }
        return instance;
    }

    /**
     * 执行后台任务（I/O密集型，如CLI命令、网络请求）
     *
     * @param task 要执行的任务
     * @return Future对象，可用于取消任务或获取结果
     */
    public Future<?> executeBackground(Runnable task) {
        logger.debug("Executing background task: {}", task.getClass().getSimpleName());
        return backgroundExecutor.submit(task);
    }

    /**
     * 执行后台任务并返回结果
     *
     * @param task 要执行的任务
     * @param <T> 任务返回类型
     * @return Future对象，可用于获取结果
     */
    public <T> Future<T> executeBackground(java.util.concurrent.Callable<T> task) {
        logger.debug("Executing background task with result: {}", task.getClass().getSimpleName());
        return backgroundExecutor.submit(task);
    }

    /**
     * 执行JavaFX Task（推荐方法，自动处理UI线程切换）
     *
     * @param task JavaFX Task
     */
    public void executeJavaFxTask(Task<?> task) {
        logger.debug("Executing JavaFX Task: {}", task.getClass().getSimpleName());

        // 设置异常处理
        task.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            logger.error("JavaFX Task failed: {}", task.getClass().getSimpleName(), exception);
        });

        // 使用传统的Thread方式执行JavaFX Task，确保与原有行为一致
        // JavaFX Task设计了特殊的线程调度机制，直接在Thread中执行最稳定
        Thread thread = new Thread(task, "SDKMAN-FXTask-" + task.getClass().getSimpleName());
        thread.start();
    }

    /**
     * 执行轻量级UI相关任务
     *
     * @param task 要执行的任务
     */
    public void executeUiTask(Runnable task) {
        logger.debug("Executing UI task: {}", task.getClass().getSimpleName());
        uiExecutor.submit(task);
    }

    /**
     * 在JavaFX应用线程中执行任务
     *
     * @param task 要执行的任务
     */
    public void runOnFxThread(Runnable task) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            task.run();
        } else {
            javafx.application.Platform.runLater(task);
        }
    }

    /**
     * 关闭线程池（应用退出时调用）
     */
    public void shutdown() {
        logger.info("Shutting down ThreadManager...");

        backgroundExecutor.shutdown();
        uiExecutor.shutdown();

        try {
            // 等待任务完成
            if (!backgroundExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
            if (!uiExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                uiExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down ThreadManager", e);
            Thread.currentThread().interrupt();
        }

        logger.info("ThreadManager shutdown completed");
    }

    /**
     * 获取线程池状态信息（用于调试）
     */
    public String getPoolStatus() {
        return String.format(
            "Background Pool: [Active: %d, Completed: %d, Queue: %d], UI Pool: [Active: %d, Completed: %d]",
            ((java.util.concurrent.ThreadPoolExecutor) backgroundExecutor).getActiveCount(),
            ((java.util.concurrent.ThreadPoolExecutor) backgroundExecutor).getCompletedTaskCount(),
            ((java.util.concurrent.ThreadPoolExecutor) backgroundExecutor).getQueue().size(),
            ((java.util.concurrent.ThreadPoolExecutor) uiExecutor).getActiveCount(),
            ((java.util.concurrent.ThreadPoolExecutor) uiExecutor).getCompletedTaskCount()
        );
    }
}