package com.shubham.featureflagplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FeatureFlagPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(FeatureFlagPlatformApplication.class, args);
	}

}
