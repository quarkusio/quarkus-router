package io.quarkus.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import io.netty.handler.codec.http.HttpRequest;

/**
 * A route specification. This can consist of the following components.
 * <p>
 * - A path, either an exact path match or a prefix mapping.
 * - A HTTP method
 * - A list of predicates. Every predicate must return true for the route to be considered valid
 */
public class Route {

    private final String path;
    private final boolean exact;
    private final String method;
    private final List<Predicate<HttpRequest>> predicates;


    private Route(Builder builder) {
        Objects.requireNonNull(builder.path, "Path cannot be null");
        this.path = builder.path;
        this.exact = builder.exact;
        this.method = builder.method;
        this.predicates = Collections.unmodifiableList(new ArrayList<>(builder.predicates));
    }

    public String getPath() {
        return path;
    }

    public boolean isExact() {
        return exact;
    }

    public String getMethod() {
        return method;
    }

    public List<Predicate<HttpRequest>> getPredicates() {
        return predicates;
    }

    public static Builder builder(String path) {
        return new Builder(path);
    }

    public static class Builder {

        private String path;
        private boolean exact;
        private String method;
        private final List<Predicate<HttpRequest>> predicates = new ArrayList<Predicate<HttpRequest>>();

        Builder(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public Builder setPath(String path) {
            Objects.requireNonNull(path, "Path must not be null");
            this.path = path;
            return this;
        }

        public boolean isExact() {
            return exact;
        }

        public Builder setExact(boolean exact) {
            this.exact = exact;
            return this;
        }

        public String getMethod() {
            return method;
        }

        public Builder setMethod(String method) {
            this.method = method;
            return this;
        }

        public List<Predicate<HttpRequest>> getPredicates() {
            return predicates;
        }

        public Builder addPredicates(Predicate<HttpRequest> predicate) {
            this.predicates.add(predicate);
            return this;
        }

        public Route build() {
            return new Route(this);
        }
    }


}
