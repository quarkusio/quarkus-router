package io.quarkus.router.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * An inbound handler to make testing of routes easier
 */
public class TestRouteHandler implements Consumer<Channel> {

    private volatile HttpRequest request;
    private volatile String body;
    private volatile ByteArrayOutputStream buffer;
    private final Function<TestRouteHandler, DefaultFullHttpResponse> responseFunction;

    public TestRouteHandler(Function<TestRouteHandler, DefaultFullHttpResponse> responseFunction) {
        this.responseFunction = wrap(responseFunction);
    }

    public TestRouteHandler(String content) {
        this(200, content);
    }

    public TestRouteHandler(int code, String content) {
        this(new Function<TestRouteHandler, DefaultFullHttpResponse>() {
            @Override
            public DefaultFullHttpResponse apply(TestRouteHandler testRouteHandler) {
                return new DefaultFullHttpResponse(testRouteHandler.request.protocolVersion(),
                        HttpResponseStatus.valueOf(code),
                        Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8)));
            }
        });
    }


    public HttpRequest getRequest() {
        return request;
    }

    public String getBody() {
        return body;
    }

    public ByteArrayOutputStream getBuffer() {
        return buffer;
    }

    @Override
    public void accept(Channel channel) {
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof HttpRequest) {
                    request = (HttpRequest) msg;
                    buffer = new ByteArrayOutputStream();
                    body = null;
                }
                if (msg instanceof HttpContent) {
                    ByteBuf buf = ((HttpContent) msg).content();
                    while (buf.isReadable()) {
                        buffer.write(0xFF & buf.readByte());
                    }
                }
                if (msg instanceof LastHttpContent) {
                    body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                    DefaultFullHttpResponse response = responseFunction.apply(TestRouteHandler.this);
                    ctx.channel().writeAndFlush(response);
                }
            }
        });
    }

    private static Function<TestRouteHandler, DefaultFullHttpResponse> wrap(Function<TestRouteHandler, DefaultFullHttpResponse> func) {
        return new Function<TestRouteHandler, DefaultFullHttpResponse>() {
            @Override
            public DefaultFullHttpResponse apply(TestRouteHandler testRouteHandler) {
                DefaultFullHttpResponse res = func.apply(testRouteHandler);
                if (res.content().isReadable()) {
                    res.headers().set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(res.content().readableBytes()));
                }
                return res;
            }
        };
    }
}
