package com.kurna.scan.init;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.Value;
import jakarta.annotation.PostConstruct;

@Component
public class AnnotationInitBean {

    @Value("${app.title2:Default App Title}")
    String appTitle;

    @Value("${app.version}")
    String appVersion;

    public String appName;

    public SpecifyInitBean specifyInitBean;

    @PostConstruct
    void init() {
        this.appName = this.appTitle + " / " + this.appVersion;
    }

    @Autowired
    void setSpecifyInitBean(SpecifyInitBean specifyInitBean) { this.specifyInitBean = specifyInitBean;}
}
