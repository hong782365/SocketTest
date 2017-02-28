package com.hong.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;

/**
 * 一个 SocketClientRequestThread 线程模拟一个客户端请求。
 * Created by derek on 2017/2/28.
 */
public class SocketClientRequestThread2 implements Runnable{

    private static final Logger logger = LoggerFactory.getLogger(SocketClientRequestThread.class);

    private CountDownLatch countDownLatch;

    /**
     * 这个线程的编号
     */
    private Integer clientIndex;

    /**
     * countDownLatch 是 java 提供的同步计数器。
     * 当计数器值减为0时，所有受其影响而等待的线程将会被激活。这样保证模拟并发请求的真实性
     * @param countDownLatch
     * @param clientIndex
     */
    public SocketClientRequestThread2(CountDownLatch countDownLatch, Integer clientIndex) {
        this.countDownLatch = countDownLatch;
        this.clientIndex = clientIndex;
    }

    @Override
    public void run() {
        Socket socket = null;
        OutputStream clientRequest = null;
        InputStream clientResponse = null;

        try {
            socket = new Socket("localhost", 83);
            clientRequest = socket.getOutputStream();
            clientResponse = socket.getInputStream();

            // 等待，直到 SocketClientDeamon 完成所有的线程的启动，然后所有线程一起发送请求
            this.countDownLatch.await();

            // 发送请求信息
            clientRequest.write(URLEncoder.encode("这是第" + this.clientIndex + "个客户端的请求11。", "UTF-8").getBytes());
            clientRequest.flush();
            clientRequest.write(URLEncoder.encode("这是第" + this.clientIndex + "个客户端的请求22。over", "UTF-8").getBytes());

            // 在这里等待，直到服务器返回信息
            logger.info("第" + this.clientIndex + "个客户端的请求发送完成，等待服务器返回信息");
            int maxLen = 1024;
            byte[] contextBytes = new byte[maxLen];
            int realLen;
            String message = "";
            // 程序执行到这里，会一直等待服务器返回信息（注意，前提是 in 和 out 都不能 close, 如果 close 了就收不到服务器的反馈了）
            while ((realLen = clientResponse.read(contextBytes, 0, maxLen)) != -1) {
                message += new String(contextBytes, 0, realLen);
            }
            logger.info("接收到服务器的信息：" + message);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (clientRequest != null) {
                    clientRequest.close();
                }
                if (clientResponse != null) {
                    clientResponse.close();
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
