package io.quarkus.router.util;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;

import io.quarkus.router.Router;

@RouterTest
public abstract class AbstractRouterTest {

    boolean setup = false;

    @BeforeEach
    public void beforeEach() throws Exception {
        if (!setup) {
            setup = true;
            Router router = Router.newInstance();
            setupRouter(router);
            RouterTestExtension.setRouter(router);
        }
    }

    public URI uri(String path) {
        return RouterTestExtension.uri(path);
    }

    protected abstract void setupRouter(Router router);
}
