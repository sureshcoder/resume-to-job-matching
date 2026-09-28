package in.sureshcoder.resumetojobmatching;

import in.sureshcoder.resumetojobmatching.matching.MatchingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MatchingProperties.class)
public class ResumeToJobMatchingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeToJobMatchingApplication.class, args);
    }

}
