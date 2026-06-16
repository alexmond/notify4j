package org.alexmond.notify4j.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal sample app. Once the library code lands (extraction #2), this will declare a
 * {@code NotificationAdapter} bean and demonstrate {@code notify4j.urls} channel config.
 */
@SpringBootApplication
public class Notify4jSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Notify4jSampleApplication.class, args);
    }
}
