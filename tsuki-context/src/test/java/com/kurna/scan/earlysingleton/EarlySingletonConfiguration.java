package com.kurna.scan.earlysingleton;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Value;

@Configuration
@ComponentScan("com.kurna.scan.earlysingleton")
public class EarlySingletonConfiguration {

    @Bean
    public EarlySingletonConsumerBean aConsumerBean(@Value("app.title") String title,
                                                    @Autowired EarlySingletonDependencyBean dependency,
                                                    @Autowired(value = false) EarlySingletonMissingDependency missingDependency) {
        return new EarlySingletonConsumerBean(title, dependency, missingDependency);
    }

    @Bean
    public EarlySingletonDependencyBean zDependencyBean() {
        return new EarlySingletonDependencyBean("dep");
    }
}
