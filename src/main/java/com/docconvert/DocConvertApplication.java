package com.docconvert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocConvertApplication {
    public static void main(String[] args) {SpringApplication.run(DocConvertApplication.class, args);
    }
}
