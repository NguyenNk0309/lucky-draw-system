plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

dependencies {
    implementation(project(":common-events"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("com.mysql:mysql-connector-j")
}
