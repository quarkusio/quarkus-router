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
import io.quarkus.router.util.AbstractRouterTest;
import io.quarkus.router.util.TestRouteHandler;

public class OnlyDefaultRouteTestCase extends AbstractRouterTest {

    @Override
    protected void setupRouter(Router router) {
        RouterRegistration registration = router.createRegistration("test", new TestRouteHandler(201, ""));
        router.setDefaultRoute(registration);
    }

    @Test
    public void testDefaultRoute() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(uri("/foo"));
            CloseableHttpResponse response = client.execute(get);
            Assertions.assertEquals(201, response.getStatusLine().getStatusCode());
            get = new HttpGet(uri("/bar"));
            response = client.execute(get);
            Assertions.assertEquals(201, response.getStatusLine().getStatusCode());
        }


    }
}
