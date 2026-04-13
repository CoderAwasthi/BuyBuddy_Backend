package com.samarthyatech.price_drop_service;

import com.mongodb.reactivestreams.client.MongoDatabase;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@EnableScheduling
@SpringBootApplication
public class PriceDropServiceApplication {

	private static final Logger logger = LoggerFactory.getLogger(PriceDropServiceApplication.class);

	@Value("${app.env}")
	private String env;
	@Value("${spring.data.mongodb.uri}")
	private String mongoUri;

	@Autowired
	ReactiveMongoTemplate template;

	@Autowired
	Environment environment;


	public static void main(String[] args) {
		SpringApplication.run(PriceDropServiceApplication.class, args);
	}

	@PostConstruct
	public void init() {
		logger.info("Active Spring profiles: {}", Arrays.toString(environment.getActiveProfiles()));
		logger.info("🚀 Running in {} mode", env);
		logger.info("📦 Mongo URI: {}", mongoUri);
		template.getMongoDatabase()
				.map(MongoDatabase::getName)
				.subscribe(name -> logger.info("DB NAME: {}", name));
	}

}
