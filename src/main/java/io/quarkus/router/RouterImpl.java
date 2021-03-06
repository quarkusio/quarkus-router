package io.quarkus.router;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

class RouterImpl implements Router {

    private final List<FilterHolder> filters = new CopyOnWriteArrayList<>();
    private final PathMatcher<PathDataHolder> routes = new PathMatcher<>();

    RouterRegistrationImpl select(HttpRequest request) {
        for (int i = 0; i < filters.size(); ++i) {
            FilterHolder filterHolder = filters.get(i);
            if (filterHolder.predicate.test(request)) {
                return filterHolder.registration;
            }
        }
        PathMatcher.PathMatch<PathDataHolder> match = routes.match(request.uri());
        if (match.getValue() == null) {
            return null;
        }
        for (; ; ) {
            PathDataHolder route = match.getValue();
            List<RouteHolder> methods = route.routesByMethod.get(request.method());
            if (methods != null) {
                for (int i = 0; i < methods.size(); ++i) {
                    RouteHolder routeHolder = methods.get(i);
                    List<Predicate<HttpRequest>> predicate = routeHolder.route.getPredicates();
                    boolean ok = true;
                    for (int j = 0; j < predicate.size(); ++j) {
                        if (!predicate.get(j).test(request)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        return routeHolder.registration;
                    }
                }
            }
            for (int i = 0; i < route.defaultRoutes.size(); ++i) {
                RouteHolder routeHolder = route.defaultRoutes.get(i);
                List<Predicate<HttpRequest>> predicate = routeHolder.route.getPredicates();
                boolean ok = true;
                for (int j = 0; j < predicate.size(); ++j) {
                    if (!predicate.get(j).test(request)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return routeHolder.registration;
                }
            }
            if (match.getMatched().equals("/")) {
                return null;
            } else {
                match = routes.match(match.getMatched().substring(0, match.getMatched().length() - 1));
            }
        }

    }

    @Override
    public Closeable addFilter(Predicate<HttpRequest> filter, Consumer<Channel> callback) {
        RouterRegistrationImpl c = new RouterRegistrationImpl(callback);
        FilterHolder holder = new FilterHolder(c, filter);
        this.filters.add(holder);
        c.closeTasks.add(new Runnable() {
            @Override
            public void run() {
                filters.remove(holder);
            }
        });
        return c;
    }

    @Override
    public Closeable addRoute(Route route, Consumer<Channel> callback) {
        RouterRegistrationImpl c = new RouterRegistrationImpl(callback);
        PathDataHolder holder = null;
        if (route.isExact()) {
            holder = routes.getExactPath(route.getPath());
            if (holder == null) {
                holder = new PathDataHolder();
            }
            routes.addExactPath(route.getPath(), holder);
        } else {
            holder = routes.getPrefixPath(route.getPath());
            if (holder == null) {
                holder = new PathDataHolder();
            }
            routes.addPrefixPath(route.getPath(), holder);
        }

        PathDataHolder finalHolder = holder;
        if (route.getMethod() != null) {
            List<RouteHolder> routes = holder.routesByMethod.computeIfAbsent(HttpMethod.valueOf(route.getMethod()), (k) -> new CopyOnWriteArrayList<>());
            RouteHolder rh = new RouteHolder(route, c);
            routes.add(rh);
            c.closeTasks.add(new Runnable() {
                @Override
                public void run() {
                    routes.remove(rh);
                }
            });
        } else {
            RouteHolder rh = new RouteHolder(route, c);
            holder.defaultRoutes.add(rh);
            c.closeTasks.add(new Runnable() {
                @Override
                public void run() {
                    finalHolder.defaultRoutes.remove(rh);
                }
            });
        }
        return c;
    }

    @Override
    public Closeable setDefaultRoute(Consumer<Channel> callback) {
        return addRoute(Route.builder("/").build(), callback);
    }

    static class PathDataHolder {

        final Map<HttpMethod, List<RouteHolder>> routesByMethod = new CopyOnWriteMap<>();

        List<RouteHolder> defaultRoutes = new CopyOnWriteArrayList<>();

    }

    static class RouteHolder {

        final Route route;
        final RouterRegistrationImpl registration;

        RouteHolder(Route route, RouterRegistrationImpl registration) {
            this.route = route;
            this.registration = registration;
        }
    }

    static class FilterHolder {
        final RouterRegistrationImpl registration;
        final Predicate<HttpRequest> predicate;

        FilterHolder(RouterRegistrationImpl registration, Predicate<HttpRequest> predicate) {
            this.registration = registration;
            this.predicate = predicate;
        }
    }

    class RouterRegistrationImpl implements Closeable {

        final Consumer<Channel> callback;
        final List<Runnable> closeTasks = new ArrayList<>();

        private RouterRegistrationImpl(Consumer<Channel> callback) {
            this.callback = callback;
        }

        public void close() {
            for (Runnable i : closeTasks) {
                i.run();
            }
        }
    }

}
