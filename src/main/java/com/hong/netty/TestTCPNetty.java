package com.hong.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
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
