package io.quarkus.router;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;

@RouterTest
public abstract class AbstractRouterTest {

    boolean setup = false;

    @BeforeEach
    public void beforeEach() throws Exception {
        if (!setup) {
            setup = true;
            RouterImpl router = new RouterImpl();
            setupRouter(router);
            RouterTestExtension.setRouter(router);
        }
    }

    public URI uri() {
        return RouterTestExtension.uri();
    }

    protected abstract void setupRouter(Router router);
}
