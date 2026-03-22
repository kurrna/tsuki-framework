package com.kurna.scan.earlysingleton;

public class EarlySingletonConsumerBean {
    public final String title;
    public final EarlySingletonDependencyBean dependency;
    public final EarlySingletonMissingDependency missingDependency;

    public EarlySingletonConsumerBean(String title,
                                      EarlySingletonDependencyBean dependency,
                                      EarlySingletonMissingDependency missingDependency) {
        this.title = title;
        this.dependency = dependency;
        this.missingDependency = missingDependency;
    }
}
