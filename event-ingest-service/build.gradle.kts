plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":events-schema"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
