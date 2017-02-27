package com.hong.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * socket套接字同样支持等待超时时间设置
 * Created by derek on 2017/2/7.
 */
public class SocketServer2TimeOut {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer2.class);

    private static Object xWait = new Object();

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerSocket serverSocket = null;

        try {
            logger.info("com.hong.bio.SocketServer2TimeOut 等待处理客户端请求...");

            serverSocket = new ServerSocket(83);
            serverSocket.setSoTimeout(100);

            while (true) {
                Socket socket = null;

                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    //===========================================================
                    //      执行到这里，说明本次accept没有接收到任何数据报文
                    //      主线程在这里就可以做一些事情，记为X
                    //===========================================================
                    synchronized (SocketServer2TimeOut.xWait) {
                        logger.info("这次没有从底层接收到任务数据报文，等待10毫秒，模拟事件X的处理时间");
                        SocketServer2TimeOut.xWait.wait(10);
                    }
                    continue;
                }

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                Integer sourcePort = socket.getPort();
                int maxLen = 2048;
                byte[] contextBytes = new byte[maxLen];
                int realLen;
                StringBuffer message = new StringBuffer();
                while ((realLen = in.read(contextBytes, 0, maxLen)) != -1) {
                    message.append(new String(contextBytes, 0, realLen));
                    /**
                     * 我们假设读取到“over”关键字，
                     * 表示客户端的所有信息在经过若干次传送后，完成
                     **/
                    if (message.indexOf("over") != -1) {
                        break;
                    }
                }

                // 下面打印信息
                logger.info("服务器收到来自于端口：" + sourcePort + "的信息：" + message);

                // 下面开始发送信息
                out.write("com.hong.bio.SocketServer2TimeOut 回发响应信息！".getBytes());

                // 关闭
                out.close();
                in.close();
                socket.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}
