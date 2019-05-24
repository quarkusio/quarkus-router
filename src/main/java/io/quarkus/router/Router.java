package io.quarkus.router;

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
 * If no routes are matched then the channel set by {@link #setDefaultRoute(RouterRegistration)} is used.
 * <p>
 * - Handlers are then matched on path. If there is an exact path mapping then this takes
 */
public interface Router {

    /**
     * Creates a router registration for use by the router.
     *
     * For any given connection the provided callback is guaranteed to be invoked once (and only once per connection)
     * before any requests are routed to the virtual channel.
     *
     * @param name The name of the registration, used for debugging purposes
     * @param connectionCallback The callback that is invoked on connection
     * @return A registration in the router
     */
    RouterRegistration createRegistration(String name, Consumer<Channel> connectionCallback);

    Router addFilter(RouterRegistration registration, Predicate<HttpRequest> filter);

    Router addRoute(RouterRegistration registration, Route route);

    Router setDefaultRoute(RouterRegistration registration);

}
