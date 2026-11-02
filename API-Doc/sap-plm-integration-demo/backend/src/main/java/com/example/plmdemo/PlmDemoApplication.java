package com.example.plmdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Learning demo for Architecture B: External PLM -> integration layer -> SAP.
 * Runs entirely on in-memory MOCK adapters. It does not create real SAP records.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PlmDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlmDemoApplication.class, args);
    }
}
