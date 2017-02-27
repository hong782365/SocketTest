package com.hong.bio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 使用多线程来优化服务器端的处理过程
 * Created by derek on 2017/2/7.
 */
public class SocketServer2 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer2.class);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(83);

        try {
            logger.info("com.hong.bio.SocketServer2 等待处理客户端请求...");
            while (true) {
                Socket socket = serverSocket.accept();
                // 当然业务处理过程可以交给一个线程（这里可以使用线程池），并且线程的创建是很耗资源的。
                // 最终改变不了 .accept() 只能一个一个接受 socket 的情况，并且被阻塞的情况
                SocketServerThread socketServerThread = new SocketServerThread(socket);
                new Thread(socketServerThread).start();
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

/**
 * 当然，接收到客户端的 socket 后，业务的处理过程可以交给一个线程来做。
 * 但还是改变不了 socket 被一个一个的做 accept() 的情况。
 * @author derek
 * @Date 2017/2/7 11:44
 */
class SocketServerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SocketServerThread.class);

    private Socket socket;

    public SocketServerThread(Socket socket) {
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
            int maxLen = 1024;
            byte[] contextBytes = new byte[maxLen];
            // 使用线程，同样无法解决 read 方法的阻塞问题，
            // 也就是说 read 方法处同样被阻塞，直到操作系统有数据准备好
            int realLen = in.read(contextBytes, 0, maxLen);
            // 读取信息
            String message = new String(contextBytes, 0, realLen);

            // 下面打印信息
            logger.info("服务器收到来自于端口：" + sourcePort + "的信息：" + message);

            // 下面开始发送信息
            out.write("com.hong.bio.SocketServer2 回发响应信息！".getBytes());

        } catch (Exception e) {
            logger.error("com.hong.bio.SocketServer2 回发响应信息异常...", e.getMessage(), e);
        } finally {
            // 试图关闭
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (this.socket != null) {
                    this.socket.close();
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

    }
}