package com.wally.customersupport.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WcsWhatsappPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(WcsWhatsappPocApplication.class, args);
    }
}
