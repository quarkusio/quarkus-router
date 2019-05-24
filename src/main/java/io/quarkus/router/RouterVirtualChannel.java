package io.quarkus.router;

import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.VoidChannelPromise;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;

public class RouterVirtualChannel implements Channel {

    private static final Logger logger = Logger.getLogger(RouterVirtualChannel.class.getName());

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final VirtualChannelUnsafe unsafe = new VirtualChannelUnsafe();
    private final DefaultChannelPipeline pipeline;
    private final ChannelPromise closePromise;

    private final ChannelConfig config = new DefaultChannelConfig(this);

    private final Channel parent;
    private final RouterHttp11Decoder decoder;
    
    private volatile boolean registered;
    private Queue<Object> inboundBuffer = new ArrayDeque<>();


    // Currently the child channel and parent channel are always on the same EventLoop thread. This allows us to
    // extend the read loop of a child channel if the child channel drains its queued data during read, and the
    // parent channel is still in its read loop. The next/previous links build a doubly linked list that the parent
    // channel will iterate in its channelReadComplete to end the read cycle for each child channel in the list.
    RouterVirtualChannel next;
    RouterVirtualChannel previous;

    /**
     * This variable represents if a read is in progress for the current channel or was requested.
     * Note that depending upon the {@link RecvByteBufAllocator} behavior a read may extend beyond the
     * {@link VirtualChannelUnsafe#beginRead()} method scope. The {@link VirtualChannelUnsafe#beginRead()} loop may
     * drain all pending data, and then if the parent channel is reading this channel may still accept frames.
     */
    private ReadStatus readStatus = ReadStatus.IDLE;

    private boolean outboundClosed;

    // We start with the writability of the channel when creating the StreamChannel.
    private volatile boolean writable;

    public RouterVirtualChannel(Channel parent, RouterHttp11Decoder decoder) {
        this.parent = parent;
        this.decoder = decoder;
        this.pipeline = new DefaultChannelPipeline(this) {

        };
        closePromise = pipeline.newPromise();
    }

    @Override
    public ChannelId id() {
        return null;
    }

    @Override
    public EventLoop eventLoop() {
        return parent.eventLoop();
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return parent.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketAddress localAddress() {
        return parent.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return parent.remoteAddress();
    }

    @Override
    public ChannelFuture closeFuture() {
        return parent.closeFuture();
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long bytesBeforeUnwritable() {
        return parent.bytesBeforeUnwritable(); //TODO
    }

    @Override
    public long bytesBeforeWritable() {
        return parent.bytesBeforeWritable(); //TODO
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return parent.alloc();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline().disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline().close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline().deregister();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline().connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline().disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline().close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline().deregister(promise);
    }

    @Override
    public Channel read() {
        pipeline().read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline().write(msg, promise);
    }

    @Override
    public Channel flush() {
        pipeline().flush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline().writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return pipeline().newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return null;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return null;
    }

    @Override
    public ChannelPromise voidPromise() {
        return null;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return false;
    }

    @Override
    public int compareTo(Channel o) {
        return 0;
    }

    /**
     * Receive a read message. This does not notify handlers unless a read is in progress on the
     * channel.
     */
    void fireChildRead(HttpObject httpObject) {
        assert eventLoop().inEventLoop();
        if (!isActive()) {
            ReferenceCountUtil.release(httpObject);
        } else if (readStatus != ReadStatus.IDLE) {
            // If a read is in progress or has been requested, there cannot be anything in the queue,
            // otherwise we would have drained it from the queue and processed it during the read cycle.
            assert inboundBuffer == null || inboundBuffer.isEmpty();
            final RecvByteBufAllocator.Handle allocHandle = unsafe.recvBufAllocHandle();
            unsafe.doRead0(httpObject, allocHandle);
            // We currently don't need to check for readEOS because the parent channel and child channel are limited
            // to the same EventLoop thread. There are a limited number of frame types that may come after EOS is
            // read (unknown, reset) and the trade off is less conditionals for the hot path (headers/data) at the
            // cost of additional readComplete notifications on the rare path.
            if (allocHandle.continueReading()) {
                decoder.tryAddChildChannelToReadPendingQueue(this);
            } else {
                decoder.tryRemoveChildChannelFromReadPendingQueue(this);
                unsafe.notifyReadComplete(allocHandle);
            }
        } else {
            if (inboundBuffer == null) {
                inboundBuffer = new ArrayDeque<Object>(4);
            }
            inboundBuffer.add(httpObject);
        }
    }

    void fireChildReadComplete() {
        assert eventLoop().inEventLoop();
        assert readStatus != ReadStatus.IDLE;
        unsafe.notifyReadComplete(unsafe.recvBufAllocHandle());
    }


    private final class VirtualChannelUnsafe implements Unsafe {
        private final VoidChannelPromise unsafeVoidPromise =
                new VoidChannelPromise(RouterVirtualChannel.this, false);
        @SuppressWarnings("deprecation")
        private RecvByteBufAllocator.Handle recvHandle;
        private boolean closeInitiated;
        private boolean readEOS;
        private boolean writeDoneAndNoFlush;

        @Override
        public void connect(final SocketAddress remoteAddress,
                            SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config().getRecvByteBufAllocator().newHandle();
                recvHandle.reset(config());
            }
            return recvHandle;
        }

        @Override
        public SocketAddress localAddress() {
            return parent().unsafe().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parent().unsafe().remoteAddress();
        }

        @Override
        public void register(EventLoop eventLoop, ChannelPromise promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (registered) {
                throw new UnsupportedOperationException("Re-register is not supported");
            }

            registered = true;
            promise.setSuccess();
            pipeline().fireChannelRegistered();
            if (isActive()) {
                pipeline().fireChannelActive();
            }
        }

        @Override
        public void bind(SocketAddress localAddress, ChannelPromise promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public void disconnect(ChannelPromise promise) {
            close(promise);
        }

        @Override
        public void close(final ChannelPromise promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (closeInitiated) {
                if (closePromise.isDone()) {
                    // Closed already.
                    promise.setSuccess();
                } else if (!(promise instanceof VoidChannelPromise)) { // Only needed if no VoidChannelPromise.
                    // This means close() was called before so we just register a listener and return
                    closePromise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            promise.setSuccess();
                        }
                    });
                }
                return;
            }
            closeInitiated = true;

            decoder.tryRemoveChildChannelFromReadPendingQueue(RouterVirtualChannel.this);

            final boolean wasActive = isActive();

            if (inboundBuffer != null) {
                for (;;) {
                    Object msg = inboundBuffer.poll();
                    if (msg == null) {
                        break;
                    }
                    ReferenceCountUtil.release(msg);
                }
            }

            // The promise should be notified before we call fireChannelInactive().
            outboundClosed = true;
            closePromise.setSuccess();
            promise.setSuccess();

            fireChannelInactiveAndDeregister(voidPromise(), wasActive);
        }

        @Override
        public void closeForcibly() {
            close(unsafe().voidPromise());
        }

        @Override
        public void deregister(ChannelPromise promise) {
            fireChannelInactiveAndDeregister(promise, false);
        }

        private void fireChannelInactiveAndDeregister(final ChannelPromise promise,
                                                      final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (!registered) {
                promise.setSuccess();
                return;
            }

            // As a user may call deregister() from within any method while doing processing in the ChannelPipeline,
            // we need to ensure we do the actual deregister operation later. This is necessary to preserve the
            // behavior of the AbstractChannel, which always invokes channelUnregistered and channelInactive
            // events 'later' to ensure the current events in the handler are completed before these events.
            //
            // See:
            // https://github.com/netty/netty/issues/4435
            invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (fireChannelInactive) {
                        pipeline().fireChannelInactive();
                    }
                    // The user can fire `deregister` events multiple times but we only want to fire the pipeline
                    // event if the channel was actually registered.
                    if (registered) {
                        registered = false;
                        pipeline().fireChannelUnregistered();
                    }
                    safeSetSuccess(promise);
                }
            });
        }

        private void safeSetSuccess(ChannelPromise promise) {
            if (!(promise instanceof VoidChannelPromise) && !promise.trySuccess()) {
                logger.log(Level.WARNING,"Failed to mark a promise as success because it is done already: {}", promise);
            }
        }

        private void invokeLater(Runnable task) {
            try {
                // This method is used by outbound operation implementations to trigger an inbound event later.
                // They do not trigger an inbound event immediately because an outbound operation might have been
                // triggered by another inbound event handler method.  If fired immediately, the call stack
                // will look like this for example:
                //
                //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
                //   -> handlerA.ctx.close()
                //     -> channel.unsafe.close()
                //       -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
                //
                // which means the execution of two inbound handler methods of the same handler overlap undesirably.
                eventLoop().execute(task);
            } catch (RejectedExecutionException e) {
                logger.log(Level.WARNING,"Can't invoke task later as EventLoop rejected it", e);
            }
        }

        @Override
        public void beginRead() {
            if (!isActive()) {
                return;
            }
            switch (readStatus) {
                case IDLE:
                    readStatus = ReadStatus.IN_PROGRESS;
                    doBeginRead();
                    break;
                case IN_PROGRESS:
                    readStatus = ReadStatus.REQUESTED;
                    break;
                default:
                    break;
            }
        }

        void doBeginRead() {
            Object message;
            if (inboundBuffer == null || (message = inboundBuffer.poll()) == null) {
                if (readEOS) {
                    unsafe.closeForcibly();
                }
            } else {
                final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
                allocHandle.reset(config());
                boolean continueReading = false;
                do {
                    doRead0((HttpObject) message, allocHandle);
                } while ((readEOS || (continueReading = allocHandle.continueReading())) &&
                        (message = inboundBuffer.poll()) != null);

                if (continueReading && decoder.parentReadInProgress && !readEOS) {
                    // Currently the parent and child channel are on the same EventLoop thread. If the parent is
                    // currently reading it is possile that more frames will be delivered to this child channel. In
                    // the case that this child channel still wants to read we delay the channelReadComplete on this
                    // child channel until the parent is done reading.
                    assert !decoder.isChildChannelInReadPendingQueue(RouterVirtualChannel.this);
                    decoder.addChildChannelToReadPendingQueue(RouterVirtualChannel.this);
                } else {
                    notifyReadComplete(allocHandle);
                }
            }
        }

        void readEOS() {
            readEOS = true;
        }

        void notifyReadComplete(RecvByteBufAllocator.Handle allocHandle) {
            assert next == null && previous == null;
            if (readStatus == ReadStatus.REQUESTED) {
                readStatus = ReadStatus.IN_PROGRESS;
            } else {
                readStatus = ReadStatus.IDLE;
            }
            allocHandle.readComplete();
            pipeline().fireChannelReadComplete();
            // Reading data may result in frames being written (e.g. WINDOW_UPDATE, RST, etc..). If the parent
            // channel is not currently reading we need to force a flush at the child channel, because we cannot
            // rely upon flush occurring in channelReadComplete on the parent channel.
            flush();
            if (readEOS) {
                unsafe.closeForcibly();
            }
        }

        @SuppressWarnings("deprecation")
        void doRead0(HttpObject httpObject, RecvByteBufAllocator.Handle allocHandle) {
            pipeline().fireChannelRead(httpObject);
            allocHandle.incMessagesRead(1);
        }

        @Override
        public void write(Object msg, final ChannelPromise promise) {
            // After this point its not possible to cancel a write anymore.
            if (!promise.setUncancellable()) {
                ReferenceCountUtil.release(msg);
                return;
            }

            if (!isActive() ||
                    // Once the outbound side was closed we should not allow header / data frames
                    outboundClosed) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(new ClosedChannelException());
                return;
            }

            try {
                if (!(msg instanceof HttpObject)) {
                    String msgStr = msg.toString();
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(new IllegalArgumentException(
                            "Message must be an " + StringUtil.simpleClassName(HttpObject.class) +
                                    ": " + msgStr));
                    return;
                }

                ChannelFuture future = write0(msg);
                if (future.isDone()) {
                    writeComplete(future, promise);
                } else {
                    future.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            writeComplete(future, promise);
                        }
                    });
                }
            } catch (Throwable t) {
                promise.tryFailure(t);
            } finally {
                writeDoneAndNoFlush = true;
            }
        }

        private void writeComplete(ChannelFuture future, ChannelPromise promise) {
            Throwable cause = future.cause();
            if (cause == null) {
                promise.setSuccess();
            } else {
                Throwable error = wrapStreamClosedError(cause);
                if (error instanceof ClosedChannelException) {
                    if (config.isAutoClose()) {
                        // Close channel if needed.
                        closeForcibly();
                    } else {
                        outboundClosed = true;
                    }
                }
                promise.setFailure(error);
            }
        }

        private Throwable wrapStreamClosedError(Throwable cause) {
            return cause;
        }

        private ChannelFuture write0(Object msg) {
            ChannelPromise channelPromise = pipeline.newPromise();
            parent.write(msg, channelPromise);
            return channelPromise;
        }

        @Override
        public void flush() {
            // If we are currently in the parent channel's read loop we should just ignore the flush.
            // We will ensure we trigger ctx.flush() after we processed all Channels later on and
            // so aggregate the flushes. This is done as ctx.flush() is expensive when as it may trigger an
            // write(...) or writev(...) operation on the socket.
            if (!writeDoneAndNoFlush || decoder.parentReadInProgress) {
                // There is nothing to flush so this is a NOOP.
                return;
            }
            try {
                parent.flush();
            } finally {
                writeDoneAndNoFlush = false;
            }
        }

        @Override
        public ChannelPromise voidPromise() {
            return unsafeVoidPromise;
        }

        @Override
        public ChannelOutboundBuffer outboundBuffer() {
            // Always return null as we not use the ChannelOutboundBuffer and not even support it.
            return null;
        }
    }


    /**
     * The current status of the read-processing for a {@link RouterVirtualChannel}.
     */
    private enum ReadStatus {
        /**
         * No read in progress and no read was requested (yet)
         */
        IDLE,

        /**
         * Reading in progress
         */
        IN_PROGRESS,

        /**
         * A read operation was requested.
         */
        REQUESTED
    }
}
