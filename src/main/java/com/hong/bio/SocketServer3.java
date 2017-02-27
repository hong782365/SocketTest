package com.hong.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 改进read()方式，让它也变成非阻塞模式
 * Created by derek on 2017/2/7.
 */
public class SocketServer3 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer3.class);

    private static Object xWait = new Object();

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(83);
            serverSocket.setSoTimeout(100);

            while (true) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    synchronized (SocketServer3.xWait) {
                        logger.info("这次没有从底层接收到任何TCP连接，等待10毫秒，模拟事件X的处理时间");
                        SocketServer3.xWait.wait(10);
                    }
                    continue;
                }

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                Integer sourcePort = socket.getPort();
                int maxLen = 2048;
                byte[] contextBytes = new byte[maxLen];
                int realLine;
                StringBuffer message = new StringBuffer();
                // 下面我们收取信息（设置成非阻塞方式，这样 read 信息的时候，又可以做一些其它事情）
                socket.setSoTimeout(10);
                BIORead:while (true) {
                    try {
                        while ((realLine = in.read(contextBytes, 0, maxLen)) != -1) {
                            message = message.append(new String(contextBytes, 0, realLine));
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
                logger.info("服务器收到来自于端口：" + sourcePort + "的信息：" + message);

                // 下面开始发送信息
                out.write("com.hong.bio.SocketServer3 回发响应信息！".getBytes());

                // 关闭
                out.close();
                in.close();
                socket.close();
            }



        } catch (IOException e) {
            logger.error("com.hong.bio.SocketServer3 : " + e.getMessage(), e);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}
