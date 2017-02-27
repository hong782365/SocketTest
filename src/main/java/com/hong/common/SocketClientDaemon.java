package com.hong.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * 模拟20个客户端并发请求，服务器端使用单线程
 * 客户端代码
 * Created by derek on 2017/2/7.
 */
public class SocketClientDaemon {

    private static final Logger logger = LoggerFactory.getLogger(SocketClientDaemon.class);

    public static void main(String[] args) throws InterruptedException {
        Integer clientNumber = 20;
        CountDownLatch countDownLatch = new CountDownLatch(clientNumber);

        // 分别开始启动这20个客户端
        for (int index = 0; index < clientNumber; index++, countDownLatch.countDown()) {
            logger.info("开始启动第【" + index + "】个客户端...");
            SocketClientRequestThread client = new SocketClientRequestThread(countDownLatch, index);
            new Thread(client).start();
        }

        // 这个 wait 不涉及到具体的实验逻辑，只是为了保证守护线程在启动所有线程后，进入等待状态
        synchronized (SocketClientDaemon.class) {
            SocketClientDaemon.class.wait();
        }
    }
}
