package com.kurna.tsuki.aop.after;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AfterApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
