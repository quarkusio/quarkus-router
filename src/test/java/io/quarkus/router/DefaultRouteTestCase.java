package io.quarkus.router;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

public class DefaultRouteTestCase extends AbstractRouterTest{


    @Override
    protected void setupRouter(Router router) {
        RouterRegistration registration = router.createRegistration("test", new Consumer<Channel>() {
            @Override
            public void accept(Channel channel) {
                channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        super.channelRead(ctx, msg);
                        if (msg instanceof LastHttpContent) {
                            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED);
                            ctx.channel().writeAndFlush(response);
                        }
                    }
                });
            }
        });
        router.setDefaultRoute(registration);
    }
    @Test
    public void testDefaultRoute() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(RouterTestExtension.uri());
            CloseableHttpResponse response = client.execute(get);
            Assertions.assertEquals(201, response.getStatusLine().getStatusCode());
        }


    }
}
