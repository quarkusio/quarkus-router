package io.quarkus.router.defaultroute;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.router.Route;
import io.quarkus.router.Router;
import io.quarkus.router.util.AbstractRouterTest;
import io.quarkus.router.util.TestRouteHandler;

public class DefaultRouteTestCase extends AbstractRouterTest {

    @Override
    protected void setupRouter(Router router) {
        router.setDefaultRoute(new TestRouteHandler(201, ""));
        router.addRoute(Route.builder("/foo").build(), new TestRouteHandler(200, "foo"));
    }

    @Test
    public void testRouterWithDefaultRoute() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(uri("/foo"));
            CloseableHttpResponse response = client.execute(get);
            Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
            Assertions.assertEquals("foo", EntityUtils.toString(response.getEntity()));

            get = new HttpGet(uri("/bar"));
            response = client.execute(get);
            Assertions.assertEquals(201, response.getStatusLine().getStatusCode());
        }
    }
}
