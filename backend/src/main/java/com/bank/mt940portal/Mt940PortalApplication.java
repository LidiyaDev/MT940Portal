package com.bank.mt940portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Mt940PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(Mt940PortalApplication.class, args);
    }
}
