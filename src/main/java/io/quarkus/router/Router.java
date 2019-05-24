package io.quarkus.router;

import java.io.Closeable;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;

/**
 * A router that can route HTTP requests to different implementations based on the contents
 * of a {@link io.netty.handler.codec.http.HttpRequest}.
 * <p>
 * The ordering is applies as follows:
 * Filters are applied in the order they are added. This allows for things like websocket or GRPC requests to be handled
 * without applying the routing logic.
 * <p>
 * Route matches are attempted in the order of most specific path to least specific path. Exact path matches are always
 * considered most specific, followed by prefix matches with the longest first.
 * <p>
 * If a Route has a HTTP method specified then it is only considered if the method is present. If there is both a route
 * with and without a method for the same path the one with a method is considered first.
 * <p>
 * Any predicates on the matching route are evaluated. If one of them returns false then this route is rejected, and the
 * next most specific route is considered instead.
 * <p>
 * If no routes are matched then the channel set by {@link #setDefaultRoute(Consumer)} is used.
 * <p>
 * - Handlers are then matched on path. If there is an exact path mapping then this takes
 */
public interface Router {

    Closeable addFilter(Predicate<HttpRequest> filter, Consumer<Channel> connectionCallback);

    Closeable addRoute(Route route, Consumer<Channel> connectionCallback);

    Closeable setDefaultRoute(Consumer<Channel> connectionCallback);

    static Router newInstance() {
        return new RouterImpl();
    }

}
