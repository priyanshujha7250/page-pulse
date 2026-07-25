package com.pagepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Page Pulse application.
 * Spring Boot auto-configures the embedded Tomcat server and component scan.
 */
@SpringBootApplication
public class PagePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagePulseApplication.class, args);
    }
}
