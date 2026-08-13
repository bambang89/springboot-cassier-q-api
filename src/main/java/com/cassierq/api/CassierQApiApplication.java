package com.cassierq.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CassierQApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CassierQApiApplication.class, args);
	}

}
