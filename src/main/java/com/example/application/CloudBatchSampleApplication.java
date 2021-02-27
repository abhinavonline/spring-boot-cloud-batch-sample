package com.example.application;

import com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudBatchSampleApplication {

    public static void main(final String[] args) {

        new SpringApplicationBuilder()
                .environment(new StandardEncryptableEnvironment())
                .sources(CloudBatchSampleApplication.class).run(args);
    }

}
