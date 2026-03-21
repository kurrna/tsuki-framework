package com.kurna.imported;

import java.time.ZonedDateTime;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Configuration;

@Configuration
public class ZonedDateConfiguration {

    @Bean
    ZonedDateTime startZonedDateTime() {
        return ZonedDateTime.now();
    }
}
