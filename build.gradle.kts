plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "de.uniwue"
version = "0.8-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("file://${rootProject.projectDir}/src/lib/repository")
    }
}

dependencies {
    // Spring Boot Starter dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-tomcat")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Spring Boot DevTools for development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // JSP and JSTL support
    implementation("jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.0")
    implementation("org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1")
    implementation("org.apache.tomcat.embed:tomcat-embed-jasper")
    
    // Jakarta APIs
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    
    // Logging (provided by Spring Boot)
    implementation("org.springframework.boot:spring-boot-starter-logging")
    
    // Image processing
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("org.openpnp:opencv:4.9.0-0")
    
    // JSON processing
    implementation("org.json:json:20230227")
    
    // File upload
    implementation("commons-fileupload:commons-fileupload:1.4")
    
    // PAGE XML dependencies
    implementation("org.primaresearch:basic:1.5b")
    implementation("org.primaresearch:Dla:1.5c")
    implementation("org.primaresearch:Io:1.5b")
    implementation("org.primaresearch:Maths:1.5b")
    
    // JTS geometry
    implementation("org.locationtech.jts:jts-core:1.18.2")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("Larex.jar")
}
