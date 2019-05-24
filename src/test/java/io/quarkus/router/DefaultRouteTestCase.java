package io.quarkus.router;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.router.util.AbstractRouterTest;
import io.quarkus.router.util.TestRouteHandler;

public class DefaultRouteTestCase extends AbstractRouterTest {

    @Override
    protected void setupRouter(Router router) {
        RouterRegistration registration = router.createRegistration("test", new TestRouteHandler(201, ""));
        router.setDefaultRoute(registration);

        registration = router.createRegistration("test2", new TestRouteHandler(200, "foo"));
        router.addRoute(registration, Route.builder("/foo").build());
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
