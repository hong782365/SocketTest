package com.hong.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实际的应用中，为了节约内存资源，我们一般不会为一个通道分配那么多的缓存空间。下面的代码我们主要对其中的缓存操作进行了优化
 * Created by derek on 2017/2/8.
 */
public class SocketServerNIO2 {

    private static final Logger logger = LoggerFactory.getLogger(SocketServerNIO2.class);

    /**
     * 改进的 java nio server 的代码中，由于 buffer 的大小设置的比较小。
     * 我们不再把一个 client 通过 socket channel 多次传给服务器的信息保存在 beff 中了（因为根本存不下）<br>
     * 我们使用 socket channel 的 hashCode 作为 key, 信息的 stringBuffer 作为 value, 存储到服务器的一个内存区域 MESSAGEHASHCONTEXT。
     */
    private static final ConcurrentHashMap<Integer, StringBuffer> MESSAGEHASHCONTEXT = new ConcurrentHashMap<>();

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
                // java 程序对 多路复用IO 的支持也包括了 阻塞模式 和 非阻塞模式 两种。
                if (selector.select(100) == 0) {
                    //================================================
                    //      这里视业务情况，可以做一些然并卵的事情
                    //================================================
                    continue;
                }
                // 这里就是本次询问操作系统，所获取到的 "所关心的事件" 的事件类型（每一个通道都是独立的）
                Iterator<SelectionKey> selectionKeys = selector.selectedKeys().iterator();

                while (selectionKeys.hasNext()) {
                    SelectionKey readyKey = selectionKeys.next();
                    // 这个已经处理的 readyKey 一定要移除。如果不移除，就会一直存在在 selector.selectedKeys 集合中
                    // 待到下一次 selector.select() > 0 时，这个 readyKey 又会被处理一次
                    selectionKeys.remove();

                    SelectableChannel selectableChannel = readyKey.channel();
                    if (readyKey.isValid() && readyKey.isAcceptable()) {
                        logger.info("===== channel 通道已经准备好 =====");
                        /**
                         * 当 server socket channel 通道已经准备好，就可以从 server socket channel 中获取 socket channel 了
                         * 拿到 socket channel 后，要做的事情就是马上到 selector 注册这人 socket channel 感兴趣的事情。
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
            logger.error("SocketServerNIO2 " + e.getMessage(), e);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    /**
     * 在 server socket channel 接收到/准备好 一个新的 TCP连接 后，
     * 就会向程序返回一个新的 socketChannel。<br>
     * 但是这个新的 socket channel 并没有在 selector "选择器/代理器" 中注册，
     * 所以程序还没法通过 selector 通知这个 socket channel 的事件。
     * 于是人们拿到新的 socket channel 后，要做的第一个事情就是
     * 到 selector "选择器/代理器" 中注册这个 socket channel 感兴趣的事件。
     * @param socketChannel 新的 socket channel
     * @param selector selector "选择器/代理器"
     * @throws IOException
     */
    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector) throws IOException {
        socketChannel.configureBlocking(false);
        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        //最后一个参数视为 为这个 socketChannel 分配的缓存区
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(50));
    }

    /**
     * 这个方法用于读取从客户端传来的信息。
     * 并且观察从客户端过来的 socket channel 在经过多次传输后，是否完成传输。
     * 如果传输完成，则返回一个 true 标记。
     * @param readyKey
     * @throws IOException
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
        // 这次，为了演示 buff 的使用方式，我们故意缩小了 buff 的容量大小到 50 byte，
        // 以便演示 channel 对 buff 的多次读写操作
        int realLen = 0;
        StringBuffer message = new StringBuffer();
        // 这句话的意思是：将目前通道中的数据写入到缓存区
        // 最大可写入的数据量就是 buff 的容量
        while ((realLen = clientSocketChannel.read(contextBytes)) != 0) {
            // 一定要把 buffer 切换成 "读" 模式，否则由于 limit = capacity
            // 在 read 没有写满的情况下，就会导致多读
            contextBytes.flip();
            int position = contextBytes.position();
            int capacity = contextBytes.capacity();
            byte[] messageBytes = new byte[capacity];
            contextBytes.get(messageBytes, position, realLen);

            // 这种方式也是可以读取数据的，而且不用关心 position 的位置。
            // 因为是目前 contextBytes 所有的数据全部转出为一个 byte 数组。
            // 使用这种方式时，一定要自己控制好读取的最终位置（realLen 很重要）
            // byte[] messageBytes = contextBytes.array();

            // 注意中文乱码的问题，使用 URLDecoder/URLEncoder, 进行解编码。
            String messageEncode = new String(messageBytes, 0, realLen, "UTF-8");
            message.append(messageEncode);

            // 再切换成 "写" 模式，直接清除缓存的方式，最快捷
            contextBytes.clear();
        }

        // 如果发现本次接收的信息中有 over 关键字，说明信息接收完了
        if (message.indexOf("over") != -1) {
            // 则从 messageHashContext 中，取出之前已经收到的信息，组合成完整的信息
            Integer channelUUID = clientSocketChannel.hashCode();
            logger.info("端口：" + resourcePort + "客户端发来的信息=====message : " + message);
            StringBuffer completeMessage;
            // 清空 MESSAGEHASHCONTEXT 中的历史记录
            StringBuffer historyMessage = MESSAGEHASHCONTEXT.remove(channelUUID);
            if (historyMessage == null) {
                completeMessage = message;
            } else {
                completeMessage = historyMessage.append(message);
            }
            //logger.info("端口：" + resourcePort + "客户端发来的完整信息=====completeMessage : " + URLDecoder.decode(completeMessage.toString(), "UTF-8"));
            logger.info("端口：" + resourcePort + "客户端发来的完整信息=====completeMessage : " + completeMessage);

            //======================================================
            //          当然接受完成后，可以在这里正式处理业务了
            //======================================================

            // 回发数据，并关闭 channel
            ByteBuffer sendBuffer = ByteBuffer.wrap("SocketServerNIO2 readSocketChannel 回发处理结果".getBytes());
            clientSocketChannel.write(sendBuffer);
            clientSocketChannel.close();
        } else {
            // 如果没有发现有 "over" 关键字，说明还没有接受完，则将本次接受到的信息存入 messageHashContext
            logger.info("端口" + resourcePort + "客户端信息还未接受完，继续接受=====message : " + message);
            // 每一个 channel 对象都是独立的，所以可以使用对象的 hash 值，作为唯一标示
            Integer channelUUID = clientSocketChannel.hashCode();

            // 然后获取这个 channel 下以前已经达到的 message 信息
            StringBuffer historyMessage = MESSAGEHASHCONTEXT.get(channelUUID);
            if (historyMessage == null) {
                historyMessage = new StringBuffer();
            }
            MESSAGEHASHCONTEXT.put(channelUUID, historyMessage.append(message));
        }
    }

}
