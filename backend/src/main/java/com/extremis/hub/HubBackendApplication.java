package com.extremis.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling powers GameDealService's periodic CheapShark refresh. */
@SpringBootApplication
@EnableScheduling
public class HubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HubBackendApplication.class, args);
	}

}
