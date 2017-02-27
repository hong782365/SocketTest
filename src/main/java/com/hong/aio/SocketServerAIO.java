//package com.hong;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.IOException;
//import java.io.UnsupportedEncodingException;
//import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
//import java.nio.channels.AsynchronousChannelGroup;
//import java.nio.channels.AsynchronousServerSocketChannel;
//import java.nio.channels.AsynchronousSocketChannel;
//import java.nio.channels.CompletionHandler;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * JAVA AIO框架测试
// * Created by derek on 2017/2/9.
// */
//public class SocketServerAIO {
//
//    private static final Object waitObject = new Object();
//
//    public static void main(String[] args) throws IOException, InterruptedException {
//        /**
//         * 对于使用的线程池技术：
//         * 1、Executors 是线程池生成工具，通过这个工具我们可以很轻松的生成 "固定大小的线程池"、"调度池"、"可伸缩线程数据的池"。
//         * 2、当然也可以通过 ThreadPoolExecutor 直接生成池。
//         * 3、这个线程池是用来得到操作系统的 "IO事件通知" 的，不是用来进行 "得到IO数据后的业务处理的"。要进行后者的操作，可以再使用一个池（最好不要混用）
//         * 4、也可以不使用线程池（不推荐），如果决定不使用线程池，直接 AsynchronousServerSocketChannel.open() 就行了。
//         */
//        ExecutorService threadPool = Executors.newFixedThreadPool(20);
//        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(threadPool);
//        final AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open(group);
//
//        // 设置要监听的端口 "0.0.0.0" 代表本机所有IP设备
//        serverSocket.bind(new InetSocketAddress("0.0.0.0", 83));
//        // 为 AsynchronousServerSocketChannel 注册监听，注意只是为 AsynchronousServerSocketChannel 通道注册监听
//        // 并不包括为 随后客户端和服务器 socketChannel 通道注册的监听
//        serverSocket.accept(null, new ServerSocketChannelHandle(serverSocket));
//
//        // 等待，以便观察现象（这个和要说解的原理本身没有任何关系，只是为了做主守护线程不会退出）
//        synchronized (waitObject) {
//            waitObject.wait();
//        }
//    }
//}
//
///**
// * 这个处理器类，专门用来响应 ServerSocketChannel 的事件。
// * ServerSocketChannel 只有一种事件：接受客户端的连接
// * @author derek
// * @Date 2017/2/9 11:43
// */
//class ServerSocketChannelHandle implements CompletionHandler<AsynchronousSocketChannel, Void> {
//
//    private static final Logger logger = LoggerFactory.getLogger(ServerSocketChannelHandle.class);
//
//    private AsynchronousServerSocketChannel serverSocketChannel;
//
//    public ServerSocketChannelHandle(AsynchronousServerSocketChannel serverSocketChannel) {
//        this.serverSocketChannel = serverSocketChannel;
//    }
//
//    /**
//     * 注意，我们分别观察 this、socketChannel、attachment 三个对象的 id。
//     * 来观察不同客户端连接到达时，这三个对象的变化，以说明 ServerSocketChannelHandle 的监听模式
//     */
//    @Override
//    public void completed(AsynchronousSocketChannel socketChannel, Void attachment) {
//        logger.info("completed(AsynchronousSocketChannel result, ByteBuffer attachment)");
//        // 每次都要重新注册监听（一次注册，一次响应），但是由于 "文件状态标示符" 是独享的，所以不需要担心有 "漏掉的" 事件
//        this.serverSocketChannel.accept(attachment, this);
//
//        // 为这个新的 socketChannel 注册 "read" 事件，以便操作系统在收到数据并准备好后，主动通知应用程序
//        // 在这里，由于我们要将这个客户端多次传输的数据累加起来一起处理，所以我们将一个 StringBuffer 对象作为一个 "附件" 依附在这个 channel 上
//        ByteBuffer readBuffer = ByteBuffer.allocate(50);
//        socketChannel.read(readBuffer, new StringBuffer(), new SocketChannelReadHandle(socketChannel, readBuffer));
//    }
//
//    /* (non-Javadoc)
//     * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable, java.lang.Object)
//     */
//    @Override
//    public void failed(Throwable exc, Void attachment) {
//        logger.info("failed(Throwable exc, ByteBuffer attachment)");
//    }
//}
//
//class SocketChannelReadHandle implements CompletionHandler<Integer, StringBuffer> {
//
//    private static final Logger logger = LoggerFactory.getLogger(SocketChannelReadHandle.class);
//
//    private AsynchronousSocketChannel socketChannel;
//
//    /**
//     * 专门用于进行这个通道数据缓存操作的 ByteBuffer <br>
//     * 当然，也可以作为 CompletionHandler 的 attachment 形式传入。<br>
//     * 这是，在这段示例代码中，attachment 被我们用来记录所有传送过来的 StringBuffer 了。
//     */
//    private ByteBuffer byteBuffer;
//
//    public SocketChannelReadHandle(AsynchronousSocketChannel socketChannel, ByteBuffer byteBuffer) {
//        this.socketChannel = socketChannel;
//        this.byteBuffer = byteBuffer;
//    }
//
//    /* (non-Javadoc)
//     * @see java.nio.channels.CompletionHandler#completed(java.lang.Object, java.lang.Object)
//     */
//    @Override
//    public void completed(Integer result, StringBuffer historyContext) {
//        // 如果条件成立，说明客户端主动终止了TCP套接字，这时服务端终止就可以了
//        if (result == -1) {
//            try {
//                this.socketChannel.close();
//            } catch (IOException e) {
//                logger.error(e.getMessage(), e);
//            }
//            return;
//        }
//
//        logger.info("completed(Integer result, Void attachment) : 然后我们来取出通道中准备好的值");
//        /**
//         * 实际上，由于我们从 Integer result 知道了本次 channel 从操作系统获取数据总长度
//         * 所以实际上，我们不需要切换成 "读模式" 的，但是为了保证编码的规范性，还是建议进行切换。
//         *
//         * 另外，无论是 JAVA AIO 框架，还是 JAVA NIO 框架，都会出现 "buffer 的容量" 小于 "当前从操作系统获取到的总数据量"，
//         * 但区别是，JAVA AIO 框架中，我们不需要专门考虑处理这样的情况，因为 JAVA AIO 框架已经帮我们做了处理（做成了多次通知）
//         */
//        this.byteBuffer.flip();
//        byte[] contexts = new byte[1024];
//        this.byteBuffer.get(contexts, 0, result);
//        this.byteBuffer.clear();
//        try {
//            String nowContent = new String(contexts, 0, result, "UTF-8");
//            historyContext.append(nowContent);
//            logger.info("=============目前的传输结果：" + historyContext);
//        } catch (UnsupportedEncodingException e) {
//            logger.error(e.getMessage(), e);
//        }
//
//        // 如果条件成立，说明还没有接收到 "结束标记"
//        if (historyContext.indexOf("over") == -1) {
//            return;
//        }
//
//        //=========================================================================
//        //          我们以“over”符号作为客户端完整信息的标记
//        //=========================================================================
//        logger.info("======收到完整信息，开始处理业务======");
//        historyContext = new StringBuffer();
//
//        // 还要继续监听（一次监听一次通知）
//        this.socketChannel.read(this.byteBuffer, historyContext, this);
//    }
//
//    /* (non-Javadoc)
//     * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable, java.lang.Object)
//     */
//    @Override
//    public void failed(Throwable exc, StringBuffer historyContext) {
//        logger.info("====发现客户端异常关闭，服务器将关闭TCP通道");
//        try {
//            this.socketChannel.close();
//        } catch (IOException e) {
//            logger.error(e.getMessage(), e);
//        }
//    }
//}