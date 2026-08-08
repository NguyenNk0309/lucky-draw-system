package com.marketplace.gateway;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) { SpringApplication.run(ApiGatewayApplication.class, args); }
    @Bean HttpClient httpClient() { return HttpClient.newHttpClient(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
