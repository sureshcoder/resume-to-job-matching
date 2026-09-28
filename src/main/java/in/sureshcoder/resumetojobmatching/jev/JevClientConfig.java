package in.sureshcoder.resumetojobmatching.jev;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(JevProperties.class)
public class JevClientConfig {

    @Bean
    RestClient jevRestClient(RestClient.Builder builder, JevProperties properties) {
        Duration timeout = properties.timeout();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        return builder.requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + (properties.hasApiKey() ? properties.apiKey() : ""))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
