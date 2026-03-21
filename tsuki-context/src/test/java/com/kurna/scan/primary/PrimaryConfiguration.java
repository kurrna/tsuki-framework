package com.kurna.scan.primary;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Primary;

@Configuration
public class PrimaryConfiguration {

    @Primary
    @Bean
    DogBean husky() {
        return new DogBean("Husky");
    }

    @Bean
    DogBean teddy() {
        return new DogBean("Teddy");
    }
}
