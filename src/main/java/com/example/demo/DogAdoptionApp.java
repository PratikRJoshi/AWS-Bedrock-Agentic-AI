package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * APPLICATION ENTRY POINT
 *
 * @SpringBootApplication is a convenience meta-annotation that combines three things:
 *   1. @Configuration       — marks this class as a source of bean definitions
 *   2. @EnableAutoConfiguration — tells Spring Boot to guess configuration based on
 *                                 the jars on the classpath (e.g., it sees spring-ai-starter-model-bedrock
 *                                 and auto-creates Bedrock client beans for us)
 *   3. @ComponentScan       — scans com.example.demo and all sub-packages for @Controller,
 *                             @Service, @Repository, @Configuration classes and registers them as beans
 *
 * WHY @SpringBootApplication instead of wiring each annotation manually?
 *   — Convention over configuration: one annotation replaces three, reducing boilerplate.
 *   — Almost every Spring Boot app uses this pattern because it auto-discovers all components
 *     under the same package tree, so you don't need to explicitly register each class.
 *
 * WHY Spring Boot instead of plain Spring Framework?
 *   — Spring Boot gives us embedded Tomcat (no WAR deployment needed), auto-configuration
 *     of data sources / AI clients / vector stores, and opinionated defaults that let us
 *     focus on business logic rather than wiring XML or Java config for every library.
 */
@SpringBootApplication
public class DogAdoptionApp {

	/**
	 * Standard Java main method — the JVM entry point.
	 *
	 * SpringApplication.run() bootstraps the entire application:
	 *   1. Creates the Spring ApplicationContext (the IoC container holding all beans)
	 *   2. Triggers auto-configuration (Bedrock clients, DataSource, VectorStore, etc.)
	 *   3. Starts the embedded Tomcat server on port 8080
	 *   4. Begins accepting HTTP requests
	 *
	 * WHY SpringApplication.run() instead of new AnnotationConfigApplicationContext()?
	 *   — SpringApplication.run() handles embedded server startup, graceful shutdown,
	 *     banner display, logging init, and profile activation — all in one call.
	 *     Using raw Spring context would require manual Tomcat setup.
	 */
	public static void main(String[] args) {
		SpringApplication.run(DogAdoptionApp.class, args);
	}
}
