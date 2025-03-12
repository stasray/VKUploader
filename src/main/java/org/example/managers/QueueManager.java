package org.example.managers;

import org.example.Main;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueManager {

    public static final BlockingQueue<Runnable> requestQueue = new LinkedBlockingQueue<>();

    // Запуск обработчика очереди
    static {
        Thread apiRequestHandler = new Thread(() -> {
            while (true) {
                try {
                    Runnable task = requestQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        task.run();
                        Thread.sleep(1000 / Main.MAX_CONCURRENT_UPLOADS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        apiRequestHandler.setDaemon(true); // Поток завершится при остановке программы
        apiRequestHandler.start();
    }

}
