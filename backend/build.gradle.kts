plugins {
	java
	id("org.springframework.boot") version "3.5.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "de.uniwue.zpd.dachs.larex"
version = "0.1.0"
description = "backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// PAGE XML / ALTO XML parsing library from Maven Central
	implementation("com.maxnth:page4j-dla:1.0.1")
	implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")

	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.keycloak:keycloak-admin-client:26.0.8")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("net.coobird:thumbnailator:0.4.20")
	implementation("org.apache.pdfbox:pdfbox:3.0.3")
	implementation("org.apache.poi:poi-ooxml:5.4.1")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("spring.config.additional-location", "optional:classpath:/application-test-overrides.yaml")
}

// Configure DevTools for better Docker support
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	// Enable automatic restart on classpath changes
	systemProperty("spring.devtools.restart.enabled", "true")

	// Configure file polling for Docker volumes (needed for non-native filesystems)
	systemProperty("spring.devtools.restart.poll-interval", "2s")
	systemProperty("spring.devtools.restart.quiet-period", "1s")

	// Enable LiveReload
	systemProperty("spring.devtools.livereload.enabled", "true")

	// JVM arguments for debugging are now passed via Docker CMD
	if (project.hasProperty("debug")) {
		jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
	}
}
