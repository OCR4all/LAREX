package de.uniwue.zpd.dachs.larex.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableCaching
public class BackendApplication {

	static {
		// Deny external DTDs and external entity references for every JDK XML parser, including
		// those created inside third-party libraries such as page4j. Uploaded XML must never be
		// able to read local files or reach internal hosts (XXE).
		System.setProperty("javax.xml.accessExternalDTD", "");
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
