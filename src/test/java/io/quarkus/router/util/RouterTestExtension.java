package io.quarkus.router.util;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.quarkus.router.Router;
import io.quarkus.router.RouterHttp11Decoder;

public class RouterTestExtension implements BeforeAllCallback {

    static volatile Router testRouter;


    static EventLoopGroup bossGroup;
    static EventLoopGroup workerGroup;
    static Channel channel;

    private static final String HOST = "localhost";
    private static final int PORT = 8765;

    public static int port() {
        return PORT;
    }

    public static String host() {
        return HOST;
    }

    public static URI uri(String path) {
        try {
            return new URI("http://" + host() + ":" + port() + (path.startsWith("/") ? path : ("/" + path)));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setRouter(Router router) {
        testRouter = router;
    }


    public static ChannelHandler createCodec() {
        return new RouterHttp11Decoder(testRouter);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (bossGroup != null) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        channel = bootstrap()
                .childHandler(new NettyHttpServerInitializer())
                .bind(host(), port()).sync().channel();

        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put(getClass().getName(), new ExtensionContext.Store.CloseableResource() {

            @Override
            public void close() throws Throwable {
                channel.close().await();
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        });
    }

    private ServerBootstrap bootstrap() {
        ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        return new ServerBootstrap()
                .option(ChannelOption.ALLOCATOR, allocator)
                .childOption(ChannelOption.ALLOCATOR, allocator)
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class);
    }
}
