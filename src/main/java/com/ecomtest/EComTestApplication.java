package com.ecomtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableRetry
public class EComTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(EComTestApplication.class, args);
	}

}
