package com.kurna.scan.init;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Value;

@Configuration
public class SpecifyInitConfiguration {

    @Bean(initMethod = "init")
    SpecifyInitBean createSpecifyInitBean(@Value("${app.title}") String appTitle, @Value("${app.version}") String appVersion) {
        return new SpecifyInitBean(appTitle, appVersion);
    }
}
