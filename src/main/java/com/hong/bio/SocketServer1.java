package com.hong.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 服务器端（com.hong.bio.SocketServer1）单个线程
 * Created by derek on 2017/2/7.
 */
public class SocketServer1 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer1.class);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(83);

        try {
            while (true) {
                //这里JAVA通过 JNI(Java native inputStream) 请求操作系统，并一直等待操作系统返回结果（或者出错）
                Socket socket = serverSocket.accept();

                // 下面我们收取信息（这里还是阻塞式的,一直等待，直到有数据可以接受）
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                Integer sourcePort = socket.getPort();
                int maxLen = 2048;
                byte[] contextBytes = new byte[maxLen];
                // read的时候，程序也会被阻塞，直到操作系统把网络传来的数据准备好。
                int realLen = in.read(contextBytes, 0, maxLen);
                // 读取信息
                String message = new String(contextBytes, 0, realLen);

                // 下面打印信息
                logger.info("服务器收到来自于端口：" + sourcePort + "的信息：" + message);

                // 下面开始发送信息
                out.write("com.hong.bio.SocketServer1 回发响应信息！".getBytes());

                // 关闭
                out.close();
                in.close();
                socket.close();
            }
        } catch (IOException e) {
            logger.error("com.hong.bio.SocketServer1 回发响应信息异常...", e.getMessage(), e);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}
