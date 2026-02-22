package bt.conference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "livekit")
@Data
public class LivekitConfig {
    public String url;
    public String apiKey;
    public String apiSecret;
}
