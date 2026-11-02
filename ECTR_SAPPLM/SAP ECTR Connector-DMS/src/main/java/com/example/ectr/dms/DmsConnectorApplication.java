// VERIFIED: standard Spring Boot entry point.
package com.example.ectr.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DmsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmsConnectorApplication.class, args);
    }
}
