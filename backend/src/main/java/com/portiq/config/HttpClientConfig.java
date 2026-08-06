package com.portiq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * The single outbound HTTP client every external call goes through.
 *
 * <p>Each caller used to hold its own {@code new RestTemplate()}, which meant the default
 * behaviour: no timeouts, and redirects followed automatically. Both matter here.
 *
 * <p>No timeout is the more likely of the two to bite. A price feed that accepts the connection and
 * then stops responding parks the request thread indefinitely, and a dashboard load fans out one
 * call per holding - so a single slow upstream is enough to exhaust the pool and take the whole
 * application down with it.
 *
 * <p>Following redirects is what turns a partly-controlled URL into a full SSRF. Even with the
 * target host pinned, a 302 from that host would be followed to anywhere, including an internal
 * address or a cloud metadata endpoint. Refusing to follow them keeps every request going exactly
 * where the code aimed it.
 */
@Configuration
public class HttpClientConfig {

    @Value("${app.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /**
     * Generous relative to the connect timeout because the upstream model calls behind the import
     * and insights features legitimately take several seconds to produce a response.
     */
    @Value("${app.http.read-timeout-ms:20000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate outboundRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
