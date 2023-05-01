package com.ae.camunda.bpm.engine.impl.cfg;

public class StandaloneInMemProcessEngineConfiguration extends org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration {
    public StandaloneInMemProcessEngineConfiguration() {
        super();
        this.jdbcUrl += ";MODE=LEGACY";
    }
}
