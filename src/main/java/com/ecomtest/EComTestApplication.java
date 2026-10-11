package com.ecomtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EComTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(EComTestApplication.class, args);
	}

}
