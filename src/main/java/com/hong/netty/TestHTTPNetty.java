package com.hong.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

/**
 * 使用 Netty 的 Http 编码/解码处理器，设计的一个简单的WEB服务器
 * Created by wangweihong on 17/2/28.
 */
public class TestHTTPNetty {

    private static final Logger logger = LoggerFactory.getLogger(TestHTTPNetty.class);

    public static void main(String[] args) {
        // 这就是主要的服务启动器
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        // ==================== 下面我们设置线程池
        EventLoopGroup bossLoopGroup = new NioEventLoopGroup(1);
        ThreadFactory threadFactory = new DefaultThreadFactory("work thread pool");
        int processorsNumber = Runtime.getRuntime().availableProcessors();
        EventLoopGroup workLoogGroup = new NioEventLoopGroup(processorsNumber * 2, threadFactory, SelectorProvider.provider());
        serverBootstrap.group(bossLoopGroup, workLoogGroup);

        // ===================== 下面设置我们服务的通道类型
        serverBootstrap.channel(NioServerSocketChannel.class);

        // ==================== 设置处理器
        serverBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
            protected void initChannel(NioSocketChannel ch) throws Exception {
                // 我们在 socket channel pipeline 中加入 http 的编码和解码器
                ch.pipeline().addLast(new HttpResponseEncoder());
                ch.pipeline().addLast(new HttpRequestDecoder());
                ch.pipeline().addLast(new HTTPServerHandler());
            }
        });

        serverBootstrap.option(ChannelOption.SO_BACKLOG, 128);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        serverBootstrap.bind(new InetSocketAddress("0.0.0.0", 83));
    }
}

class HTTPServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HTTPServerHandler.class);

    /**
     * 由于一次 httpContent 可能没有传输完全部的请求信息。所以这里要做一个连续的记录
     * 然后在 channelReadComplete 方法中（执行了这个方法说明这次所有的 http 内容都传输完了）进行处理
     */
    private static AttributeKey<StringBuffer> CONNTENT = AttributeKey.valueOf("content");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.info("channelRead(ChannelHandlerContext ctx, Object msg)");
        /**
         * 在测试中，我们首先取出客户端传来的参数、URL信息，并且返回给一个确认信息。
         * 要使用 HTTP 服务，我们首先要了解 Netty 中 http 的格式，如下：
         * ----------------------------------------------
         * | http request | http content | http content |
         * ----------------------------------------------
         *
         * 所以通过 httpRequestDecoder channel handler 解码后的 msg 可能是两种类型：
         * httpRequest: 里面包含了请求 head、请求的 url 等信息
         * httpContent: 请求的主体内容
         */
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            HttpMethod method = request.getMethod();

            String methodName = method.name();
            String uri = request.getUri();
            logger.info("methodName = " + methodName + " && url = " + uri);
        }

        // 如果条件成立，则在这个代码段实现 http 请求内容的累加
        if (msg instanceof HttpContent) {
            StringBuffer content = ctx.attr(HTTPServerHandler.CONNTENT).get();
            if (content == null) {
                content = new StringBuffer();
                ctx.attr(HTTPServerHandler.CONNTENT).set(content);
            }

            HttpContent httpContent = (HttpContent) msg;
            ByteBuf contentBuf = httpContent.content();
            String preContent = contentBuf.toString(CharsetUtil.UTF_8);
            content.append(preContent);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("channelReadComplete(ChannelHandlerContext ctx)");

        /**
         * 一旦本次 http 请求传输完成，则可以进行业务处理了。
         * 并且返回响应
         */
        StringBuffer content = ctx.attr(HTTPServerHandler.CONNTENT).get();
        logger.info("http 客户端传来的信息为：" + content);

        // 开始返回信息
        String returnValue = "return response";
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders httpHeaders = response.headers();
        // 这些就是 http response 的 head 信息咯，参见 http 规范。另外还可以设置自己的 head 属性
        httpHeaders.add("param", "value");
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
        // 一定要设置长度，否则 http 客户端会一直等待（因为返回的信息长度客户端不知道）
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, returnValue.length());

        ByteBuf responseContent = response.content();
        responseContent.writeBytes(returnValue.getBytes("UTF-8"));

        // 开始返回
        ctx.writeAndFlush(response);
    }
}
