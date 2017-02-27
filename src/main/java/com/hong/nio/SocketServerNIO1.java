package com.hong.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * 使用JAVA NIO框架，实现一个支持多路复用IO的服务器端（实际上客户端是否使用多路复用IO技术，对整个系统架构的性能提升相关性不大）：
 * Created by derek on 2017/2/8.
 */
public class SocketServerNIO1 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServerNIO1.class);

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        ServerSocket serverSocket = serverChannel.socket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(83));

        Selector selector = Selector.open();
        // 注意：服务器通道只能注册 SelectionKey.OP_ACCEPT 事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        try {
            while (true) {
                // 如果条件成立，说明本次询问 selector, 并没有获取到任何准备好的、感兴趣的事件
                // java 程序对 多路复用IO 的支持也包括了 阻塞模式 和 非阻塞模式
                if (selector.select(100) == 0) {
                    //================================================
                    //      这里视业务情况，可以做一些然并卵的事情
                    //================================================
                    continue;
                }
                // 这里就是本次询问操作系统，所获取到的 “所关心的事件” 的事件类型（每一个通道都是独立的）
                Iterator<SelectionKey> selectionKeys = selector.selectedKeys().iterator();

                while (selectionKeys.hasNext()) {
                    SelectionKey readyKey = selectionKeys.next();
                    // 这个已经处理的　readyKey 一定要移除。如果不黎玲，就会一直存在在 selector.selectedKeys集合中
                    // 待到下一次 selector.select() > 0 时，这个 readyKey 又会被处理一次
                    selectionKeys.remove();

                    SelectableChannel selectableChannel = readyKey.channel();
                    if (readyKey.isValid() && readyKey.isAcceptable()) {
                        logger.info("====== channel 通道已经准备好 ======");
                        /**
                         * 当 server socket channel 通道已经准备好，就可以从 server socket channel 中获取 socket channel 了
                         * 拿到 socket channel 后，要做的事情就是马上到 selector 注册这个 socket channel 感兴趣的事情。
                         * 否则无法监听到这个 socket channel 到达的数据
                         */
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        registerSocketChannel(socketChannel, selector);
                    } else if (readyKey.isValid() && readyKey.isConnectable()) {
                        logger.info("===== socket channel 建立连接 =====");
                    } else if (readyKey.isValid() && readyKey.isReadable()) {
                        logger.info("===== socket channel 数据准备完成，可以去读==读取 =====");
                        readSocketChannel(readyKey);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("SocketServerNIO1 : " + e.getMessage(), e);
        } finally {
            serverSocket.close();
        }
    }

    /**
     * 在 server socket channel 接收到/准备好 一个新的 TCP连接 后
     * 就会向程序返回一个新的 socketChannel。<br>
     * 但是这个新的 socket channel 并没有在 selector "选择器/代理器" 中注册,
     * 所以程序还没法通过 selector 通知这个 socket channel 的事件。
     * 于是我们拿到新的 socket channel 后，要做的第一个事情就是
     * 到 selector "选择器/代理器" 中注册这个 socket channel 感兴趣的事件
     * @param socketChannel 新的 socket channel
     * @param selector selector "选择器/代理器"
     * @throws IOException
     */
    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector) throws IOException {
        socketChannel.configureBlocking(false);
        // socket 通道可以且只可以注册三种事件 SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(2048));
    }

    /**
     * 这个方法用于读取客户端传来的信息。
     * 并且观察从客户端过来的 socket channel 在经过多次传输后，是否完成传输。
     * 如果传输完成，则返回一个 true 的标记。
     * @param readyKey
     */
    private static void readSocketChannel(SelectionKey readyKey) throws IOException {
        SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
        // 获取客户端使用的端口
        InetSocketAddress sourceSocketAddress = (InetSocketAddress) clientSocketChannel.getRemoteAddress();
        Integer resourcePort = sourceSocketAddress.getPort();

        // 拿到这个 socket channel 使用的缓存区，准备读取数据
        // 缓存区，实际上重要的就是三个元素 capacity, position 和 limit。
        ByteBuffer contextBytes = (ByteBuffer) readyKey.attachment();
        // 将通道的数据写入到缓存区，注意是写入到缓存区。
        int realLen = -1;
        try {
            realLen = clientSocketChannel.read(contextBytes);
        } catch (IOException e) {
            // 这里抛出了异常，一般就是客户端因为某种原因终止了。所以关闭 channel 就行了
            logger.error("SocketServerNIO1 readSocketChannel " + e.getMessage(), e);
            clientSocketChannel.close();
            return;
        }

        // 如果缓存区中没有任何数据（但实际上这个不太可能，否则就不会触发 OP_READ 事件了）
        if (realLen == -1) {
            logger.warn("==== 缓存区没有数据？ ====");
            return;
        }

        // 将缓存区从写状态切换为读状态（实际上这个方法是读写模式互切换）。
        // 这时 java nio 框架中的这个 socket channel 的写请求将全部等待。
        contextBytes.flip();
        // 注意中文乱码的问题，使用 URLDecoder/URLEncoder 进行解编码。
        byte[] messageBytes = contextBytes.array();
        String messageEncode = new String(messageBytes, "UTF-8");
        String message = URLDecoder.decode(messageEncode, "UTF-8");

        // 如果收到了 "over" 关键字，才会清空 buffer, 并回发数据；
        // 否则不清空缓存，还要还原 buffer 的 "写状态"
        if (message.indexOf("over") != -1) {
            // 清空已经读取的缓存，并从新切换为写状态（这里要注意 clear() 和 capacity() 两个方法的区别）
            contextBytes.clear();
            logger.info("端口：" + resourcePort + "客户端发来的信息=====message : " + message);

            //======================================================
            //          当然接受完成后，可以在这里正式处理业务了
            //======================================================

            // 回发数据，并关闭 channel
            //ByteBuffer sendBuffer = ByteBuffer.wrap(URLEncoder.encode("SocketServerNIO1 readSocketChannel 回发处理结果", "UTF-8").getBytes());
            ByteBuffer sendBuffer = ByteBuffer.wrap(("SocketServerNIO1 readSocketChannel 回发处理结果").getBytes());
            clientSocketChannel.write(sendBuffer);
            clientSocketChannel.close();
        } else {
            logger.info("端口：" + resourcePort + "客户端信息还未接受完，继续接受=====message : " + message);
            // 这时, limit 和 capacity 的值一致, position 的位置是 realLen 的位置
            contextBytes.position(realLen);
            contextBytes.limit(contextBytes.capacity());
        }
    }
}
