package com.privatbank.cardsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CardSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardSenderApplication.class, args);
    }

}
