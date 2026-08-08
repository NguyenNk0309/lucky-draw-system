plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.marketplace"
    version = "1.0.0"

    repositories { mavenCentral() }
}

subprojects {
    tasks.withType<Test> { useJUnitPlatform() }
}

