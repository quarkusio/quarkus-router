package io.quarkus.router;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2MultiplexCodec;

/**
 * handler that routes incoming HTTP requests
 *
 * This class maintains thread safety by delegating all operations to the registered event loop group
 *
 */
public class RouterHttp11Decoder extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(RouterHttp11Decoder.class.getName());

    private RouterVirtualChannel activeChannel;
    private ChannelHandlerContext ctx;

    //if we receive a new request before the
    private Queue<HttpObject> queuedData = new LinkedList<>();
    private boolean currentRequestDone;

    private final RouterImpl router;

    //TODO: better data structure
    private final IdentityHashMap<RouterImpl.RouterRegistrationImpl, RouterVirtualChannel> channelMap = new IdentityHashMap<>();


    boolean parentReadInProgress;
    private RouterVirtualChannel head;
    private RouterVirtualChannel tail;

    public RouterHttp11Decoder(Router router) {
        this.router = (RouterImpl) router;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.ctx = ctx;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        this.ctx = null;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        handleHttpMessage(msg);
    }

    boolean isChildChannelInReadPendingQueue(RouterVirtualChannel childChannel) {
        return childChannel.previous != null || childChannel.next != null || head == childChannel;
    }

    final void tryAddChildChannelToReadPendingQueue(RouterVirtualChannel childChannel) {
        if (!isChildChannelInReadPendingQueue(childChannel)) {
            addChildChannelToReadPendingQueue(childChannel);
        }
    }

    final void addChildChannelToReadPendingQueue(RouterVirtualChannel childChannel) {
        if (tail == null) {
            assert head == null;
            tail = head = childChannel;
        } else {
            childChannel.previous = tail;
            tail.next = childChannel;
            tail = childChannel;
        }
    }

    void tryRemoveChildChannelFromReadPendingQueue(RouterVirtualChannel childChannel) {
        if (isChildChannelInReadPendingQueue(childChannel)) {
            removeChildChannelFromReadPendingQueue(childChannel);
        }
    }

    private void removeChildChannelFromReadPendingQueue(RouterVirtualChannel childChannel) {
        RouterVirtualChannel previous = childChannel.previous;
        if (childChannel.next != null) {
            childChannel.next.previous = previous;
        } else {
            tail = tail.previous; // If there is no next, this childChannel is the tail, so move the tail back.
        }
        if (previous != null) {
            previous.next = childChannel.next;
        } else {
            head = head.next; // If there is no previous, this childChannel is the head, so move the tail forward.
        }
        childChannel.next = childChannel.previous = null;
    }

    final void onChannelReadComplete(ChannelHandlerContext ctx)  {
        // If we have many child channel we can optimize for the case when multiple call flush() in
        // channelReadComplete(...) callbacks and only do it once as otherwise we will end-up with multiple
        // write calls on the socket which is expensive.
        RouterVirtualChannel current = head;
        while (current != null) {
            RouterVirtualChannel childChannel = current;
            // Clear early in case fireChildReadComplete() causes it to need to be re-processed
            current = current.next;
            childChannel.next = childChannel.previous = null;
            childChannel.fireChildReadComplete();
        }
    }


    /**
     * Notifies any child streams of the read completion.
     */
    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        try {
            onChannelReadComplete(ctx);
        } finally {
            parentReadInProgress = false;
            tail = head = null;
            ctx.flush();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        parentReadInProgress = true;
        super.channelRead(ctx, msg);
    }


    private void handleHttpMessage(HttpObject msg) {
        if (msg instanceof HttpRequest) {
            if(activeChannel != null) {
                currentRequestDone = true;
                queuedData.add(msg);
            } else {
                HttpRequest request = (HttpRequest) msg;
                RouterImpl.RouterRegistrationImpl result = router.select(request);
                if(result == null) {
                    DefaultFullHttpResponse notFound = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND);
                    ctx.channel().write(notFound);
                    return;
                }
                RouterVirtualChannel existing = channelMap.get(result);
                if(existing == null) {
                    existing = new RouterVirtualChannel(ctx.pipeline().channel(), this);
                    result.callback.accept(existing);
                    channelMap.put(result, existing);
                }
                activeChannel = existing;
                existing.fireChildRead(msg);
            }
        } else if (activeChannel != null) {
            if(currentRequestDone) {
                queuedData.add(msg);
            } else {
                if (msg instanceof LastHttpContent) {
                    currentRequestDone = true;
                }
                activeChannel.fireChildRead(msg);
            }
        } else {
            logger.log(Level.SEVERE, "Received HttpObject that was not a request with no channel active {}", msg);
        }
    }

    void activeChannelComplete() {
        if(ctx.channel().eventLoop().inEventLoop()) {
            activeChannel = null;
            currentRequestDone = false;
            while (!queuedData.isEmpty() && !currentRequestDone) {
                handleHttpMessage(queuedData.poll());
            }
        } else {
            ctx.channel().eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    activeChannelComplete();
                }
            });
        }
    }

}
