package com.angelo.paymentsflow;

import com.angelo.paymentsflow.config.TransferProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the paymentsflow service: a payments and double-entry ledger
 * REST microservice.
 */
@SpringBootApplication
@EnableConfigurationProperties(TransferProperties.class)
public class PaymentsflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsflowApplication.class, args);
    }
}
