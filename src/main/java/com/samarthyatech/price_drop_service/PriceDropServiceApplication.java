package com.samarthyatech.price_drop_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PriceDropServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PriceDropServiceApplication.class, args);
	}

}
