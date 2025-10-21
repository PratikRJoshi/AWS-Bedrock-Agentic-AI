package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.CrudRepository;
import software.amazon.awssdk.services.sts.endpoints.internal.Value;

import java.util.List;

@SpringBootApplication
public class DogAdoptionApp {

	public static void main(String[] args) {
		SpringApplication.run(DogAdoptionApp.class, args);
	}
}
