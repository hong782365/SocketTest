package com.hong.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 通过加入线程的概念，让socket server能够在应用层面，
 * 通过非阻塞的方式同时处理多个socket套接字
 * Created by derek on 2017/2/7.
 */
public class SocketServer4 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer4.class);

    private static Object xWait = new Object();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(83);
        serverSocket.setSoTimeout(100);

        try {
            while (true) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    synchronized (SocketServer4.xWait) {
                        logger.info("这次没有从底层接收到任何TCP连接，等待10毫秒，模拟事件X的处理时间");
                        SocketServer4.xWait.wait(10);
                    }
                    continue;
                }
                // 当然业务处理过程可以交给一个线程（这里可以使用线程池）, 并且线程的创建是很耗资源的。
                // 最终改变不了 .accept() 只能一个一个接受 socket 连接的情况
                SocketServer4Thread socketServer4Thread = new SocketServer4Thread(socket);
                new Thread(socketServer4Thread).start();
            }
        } catch (Exception e) {
            logger.error("com.hong.bio.SocketServer4 : " + e.getMessage(), e);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}

/**
 * 当然业务处理过程可以交给一个线程（这里可以使用线程池）, 并且线程的创建是很耗资源的。
 * 最终改变不了 .accept() 只能一个一个接受 socket 连接的情况
 * @author derek
 * @Date 2017/2/7 20:44
 */
class SocketServer4Thread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer4Thread.class);

    private Socket socket;

    public SocketServer4Thread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        InputStream in = null;
        OutputStream out = null;

        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Integer sourcePort = socket.getPort();
            int maxLen = 2048;
            byte[] contextBytes = new byte[maxLen];
            int realLen;
            StringBuffer message = new StringBuffer();
            // 下面我们收取信息（设置成非阻塞方式，这样 read 信息的时候，又可以做一些其它事情）
            this.socket.setSoTimeout(10);
            BIORead:while (true) {
                try {
                    while ((realLen = in.read(contextBytes, 0, maxLen)) != -1) {
                        message.append(new String(contextBytes, 0, realLen));
                        /**
                         * 我们假设读取到“over”关键字，
                         * 表示客户端的所有信息在经过若干次传送后，完成
                         **/
                        if (message.indexOf("over") != -1) {
                            break BIORead;
                        }
                    }
                } catch (IOException e) {
                    //===========================================================
                    //      执行到这里，说明本次read没有接收到任何数据流
                    //      主线程在这里又可以做一些事情，记为Y
                    //===========================================================
                    logger.info("这次没有从底层接收到任务数据报文，等待10毫秒，模拟事件Y的处理时间");
                    continue;
                }
            }
            // 下面打印信息
            Long threadId = Thread.currentThread().getId();
            logger.info("服务器(线程：" + threadId + ")收到来自于端口：" + sourcePort + "的信息：" + message);

            // 下面开始发送信息
            out.write("com.hong.bio.SocketServer4Thread : 回发响应信息！".getBytes());

            // 关闭
            out.close();
            in.close();
            this.socket.close();
        } catch (IOException e) {
            logger.error("com.hong.bio.SocketServer4Thread : " + e.getMessage(), e);
        }
    }
}