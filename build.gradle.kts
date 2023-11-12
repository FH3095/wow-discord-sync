
plugins {
	eclipse
	"eclipse-wtp"
    java
    war
}

repositories {
    mavenCentral()
}

allprojects {
    group = "eu.4fh"
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
	implementation("eu.4fh:abstract-bnet-api") {
		version {
			branch = "main"
		}
	}
    implementation("net.dv8tion:JDA:5.0.+") {
        exclude("club.minnced", "opus-java")
    }
	implementation("com.github.spotbugs:spotbugs-annotations:4.8.1")
	//implementation("com.google.guava:guava:31.1-jre")
	//implementation("org.reflections:reflections:0.10.2")
	implementation("com.zaxxer:HikariCP:5.1.0")
	implementation("org.hibernate.orm:hibernate-core:6.3.1.Final")
	implementation("org.apache.httpcomponents.core5:httpcore5:5.2.3")
	// REST server-side
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    implementation("org.glassfish.jersey.containers:jersey-container-servlet:3.1.3")
    implementation("org.glassfish.jersey.inject:jersey-hk2:3.1.3")
    // Logs
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-jdk14:2.0.9")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.+")
	compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
	testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.assertj:assertj-db:2.0.2")
    testImplementation("org.easymock:easymock:5.0.0")
    testImplementation("org.hsqldb:hsqldb:2.7.+")
}

val test by tasks.getting(Test::class) {
    // Use junit platform for unit tests
    useJUnitPlatform()
}

tasks.register<Copy>("warToTomcat") {
	dependsOn("war")
	from(layout.buildDirectory.dir("libs"))
	include("*.war")
	into(layout.buildDirectory.dir("../../../Tomcat/webapps"))
}
