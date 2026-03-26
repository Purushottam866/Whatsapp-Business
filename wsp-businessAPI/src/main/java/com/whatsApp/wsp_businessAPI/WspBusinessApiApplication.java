package com.whatsApp.wsp_businessAPI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class WspBusinessApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(WspBusinessApiApplication.class, args);
	}
 
}
   