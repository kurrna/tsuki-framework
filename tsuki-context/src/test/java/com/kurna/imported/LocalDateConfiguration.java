package com.kurna.imported;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Configuration;

@Configuration
public class LocalDateConfiguration {

    @Bean
    LocalDate startLocalDate() {
        return LocalDate.now();
    }

    @Bean
    LocalDateTime startLocalDateTime() {
        return LocalDateTime.now();
    }
}
