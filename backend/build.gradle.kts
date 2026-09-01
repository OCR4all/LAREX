plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "de.uniwue.zpd.dachs.larex"
version = "1.0.0-SNAPSHOT"
description = "backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.maxnth:page4j-dla:1.2.0")
	implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.keycloak:keycloak-admin-client:26.0.12")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("net.coobird:thumbnailator:0.4.20")
	implementation("org.apache.pdfbox:pdfbox:3.0.3")
	implementation("org.apache.pdfbox:xmpbox:3.0.3")
	implementation("org.apache.poi:poi-ooxml:5.4.1")
	implementation("net.sf.saxon:Saxon-HE:12.5")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.github.dasniko:testcontainers-keycloak:3.4.0")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	maxHeapSize = "2g"
	systemProperty("spring.config.additional-location", "optional:classpath:/application-test-overrides.yaml")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	// Backups and imports are throughput-heavy; C1-only optimized launch makes them pathologically slow in dev.
	optimizedLaunch.set(false)
}
