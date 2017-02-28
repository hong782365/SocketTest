package com.hong.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

/**
 * Created by derek on 2017/2/10.
 */
public class TestTCPNetty {

    private static final Logger logger = LoggerFactory.getLogger(TestTCPNetty.class);

    public static void main(String[] args) {
        // 这就是主要的服务启动器
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        // ================ 下面我们设置线程池
        // BOSS线程池
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        // WORK 线程池 ： 这样的申明方式，主要是为了说明 Netty 的线程组是怎样工作的
        ThreadFactory threadFactory = new DefaultThreadFactory("work thread pool");
        // CPU 个数
        int processorsNumber = Runtime.getRuntime().availableProcessors();
        EventLoopGroup workLoopGroup = new NioEventLoopGroup(processorsNumber * 2, threadFactory, SelectorProvider.provider());
        // 指定 Netty 的 Boss线程 和 work线程
        serverBootstrap.group(bossLoopGroup, workLoopGroup);
        // 如果是以下的申明方式，说明 BOSS线程 和 WORK线程共享一个线程池
        // （实际上一般的情况环境下，这种共享线程池的方式已经够了）
        // serverBootstrap.group(workLoopGroup);

        // ================ 下面我们设置我们服务的通道类型
        // 只能是实现了 ServerChannel 接口的 "服务器"通道类
        serverBootstrap.channel(NioServerSocketChannel.class);
        // 当然也可以这样创建
//        serverBootstrap.channelFactory(new ChannelFactory<NioServerSocketChannel>() {
//            @Override
//            public NioServerSocketChannel newChannel() {
//                return new NioServerSocketChannel(SelectorProvider.provider());
//            }
//        });

        // ================ 设置处理器
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                ch.pipeline().addLast(new ByteArrayEncoder());
                ch.pipeline().addLast(new TCPServerHandler());
                ch.pipeline().addLast(new ByteArrayDecoder());
            }
        });

        // =========================== 设置 netty 服务器绑定的 ip 和 端口
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 128);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        serverBootstrap.bind(new InetSocketAddress("0.0.0.0", 83));
        // 还可以监控多个端口
        // serverBootstrap.bind(new InetSocketAddress("0.0.0.0", 84));
    }
}

class TCPServerHandler extends ChannelInboundHandlerAdapter {

    private static Logger logger = LoggerFactory.getLogger(TCPServerHandler.class);

    /**
     * 之前引入的是 netty-all-4.0.4.Final 没有 AttributeKey.valueOf, 更改版本后，引入 netty-all-4.1.3.Final 之后就有了。
     * 每一个 channel, 都有独立的 handler、ChannelHandlerContext、ChannelPipeline、Attribute
     * 所以不需要担心多个 channel 中的这些对象相互影响。<br>
     * 这里我们使用 content 这个 key, 记录这个 handler 中已经接收到的客户端信息。
     */
    private static AttributeKey<StringBuffer> content = AttributeKey.valueOf("content");

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRegistered()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelRegistered(ctx)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelUnregistered()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelUnregistered((ctx)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelActive()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelActive((ctx)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelInactive()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelInactive((ctx)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     * @param msg
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.info("channelRead(ChannelHandlerContext ctx, Object msg)");
        /**
         * 我们使用 IDE 工具模拟长连接中的数据缓慢提交。
         * 由 read 方法负责接收数据，但只是进行数据累加，不进行任何处理
         */
        ByteBuf byteBuf = (ByteBuf) msg;
        try {
            StringBuffer contextBuffer = new StringBuffer();
            while (byteBuf.isReadable()) {
                contextBuffer.append((char) byteBuf.readByte());
            }

            // 加入临时区域
            StringBuffer content = ctx.attr(TCPServerHandler.content).get();
            if (content == null) {
                content = new StringBuffer();
                ctx.attr(TCPServerHandler.content).set(content);
            }
            content.append(contextBuffer);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        } finally {
            byteBuf.release();
        }
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelReadComplete()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelReadComplete(ChannelHandlerContext ctx)");
        /**
         * 由 readComplete 方法负责检查数据是否接收完了。
         * 和之前的文章一样，我们检查整个内容中是否有 "over" 关键字
         */
        StringBuffer content = ctx.attr(TCPServerHandler.content).get();
        // 如果条件成立说明还没有接收到完整客户端信息
        if (content.indexOf("over") == -1) {
            return;
        }

        // 当接收到信息后，首先要做的是清空原来的历史信息
        ctx.attr(TCPServerHandler.content).set(new StringBuffer());

        // 准备向客户端发送响应
        ByteBuf byteBuf = ctx.alloc().buffer(1024);
        byteBuf.writeBytes("回发响应信息！".getBytes());
        ctx.writeAndFlush(byteBuf);

        /**
         * 关闭，正常终止这个通道上下文，就可以关闭通道了
         * （如果不关闭，这个通道的回话将一直存在，只要网络是稳定的，服务器就可以随时通过这个会话向客户端发送信息。）
         * 关闭通道意味着 TCP 将正常断开，其中所有的
         * handler、ChannelHandlerContext、ChannelPipiline、Attribute等信息都将注销
         */
        ctx.close();
    }

    /**
     * Calls {@link ChannelHandlerContext#fireUserEventTriggered(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     * @param evt
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.info("super.userEventTriggered(ctx, evt)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelWritabilityChanged()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.channelWritabilityChanged((ctx)");
    }

    /**
     * Calls {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} to forward
     * to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     * @param cause
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("super.exceptionCaught(ctx, cause)");
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     *
     * @param ctx
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.handlerAdded((ctx)");
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     *
     * @param ctx
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        logger.info("super.handlerRemoved((ctx)");
    }
}