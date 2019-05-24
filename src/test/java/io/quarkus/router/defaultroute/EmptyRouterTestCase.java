package io.quarkus.router.defaultroute;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.router.Router;
import io.quarkus.router.util.AbstractRouterTest;
import io.quarkus.router.util.RouterTestExtension;

public class EmptyRouterTestCase extends AbstractRouterTest {


    @Override
    protected void setupRouter(Router router) {
    }

    @Test
    public void testEmptyRouter() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(uri("/foo"));
            CloseableHttpResponse response = client.execute(get);
            Assertions.assertEquals(404, response.getStatusLine().getStatusCode());
             get = new HttpGet(uri("/bar"));
            response = client.execute(get);
            Assertions.assertEquals(404, response.getStatusLine().getStatusCode());
        }


    }
}
