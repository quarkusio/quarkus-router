package io.quarkus.router.post;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.router.Route;
import io.quarkus.router.Router;
import io.quarkus.router.util.AbstractRouterTest;
import io.quarkus.router.util.TestRouteHandler;

public class RouterPostRequestTestCase extends AbstractRouterTest {

    public static final String HELLO_WORLD = "Hello World";

    @Override
    protected void setupRouter(Router router) {
        router.setDefaultRoute(new TestRouteHandler(new Function<TestRouteHandler, DefaultFullHttpResponse>() {
            @Override
            public DefaultFullHttpResponse apply(TestRouteHandler testRouteHandler) {
                return new DefaultFullHttpResponse(testRouteHandler.getRequest().protocolVersion(), HttpResponseStatus.OK, Unpooled.copiedBuffer(("default:" + testRouteHandler.getBody()).getBytes(StandardCharsets.UTF_8)));
            }
        }));
        router.addRoute(Route.builder("/foo").build(), new TestRouteHandler(new Function<TestRouteHandler, DefaultFullHttpResponse>() {
            @Override
            public DefaultFullHttpResponse apply(TestRouteHandler testRouteHandler) {
                return new DefaultFullHttpResponse(testRouteHandler.getRequest().protocolVersion(), HttpResponseStatus.OK, Unpooled.copiedBuffer(("foo:" + testRouteHandler.getBody()).getBytes(StandardCharsets.UTF_8)));
            }
        }));

    }


    @Test
    public void testPostRequestRouting() throws Exception {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
            for (int i = 0; i < 20; ++i) {
                try {
                    for (int j = 0; j < 10000; ++j) {
                        builder.append(HELLO_WORLD);
                    }
                    String message = builder.toString();

                    HttpPost post = new HttpPost(uri("bar"));
                    post.setEntity(new StringEntity(message));

                    CloseableHttpResponse response = client.execute(post);
                    Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
                    Assertions.assertEquals("default:" + message, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));

                    post = new HttpPost(uri("foo"));
                    post.setEntity(new StringEntity(message));

                    response = client.execute(post);
                    Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
                    Assertions.assertEquals("foo:" + message, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));

                } catch (Throwable e) {
                    throw new RuntimeException("test failed with i equal to " + i, e);
                }
            }
        }
    }
}
