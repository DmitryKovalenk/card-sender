package com.privatbank.cardsender.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    /**
     * Конфіг Swagger, цього достатньо для запуска.
     * Додав для опису API і це позбавить необхідності смикати запити через Postman.
     */
    @Bean
    OpenAPI cardSenderOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Card Sender API")
                .version("1.0")
                .description("Створіть замовлення на картку, згенеруйте її номер, " +
                             "надішліть запит на відправлення та перевірте статус. " +
                             "Поле X-Operator фіксує оператора, що виконав дію; це не є процедурою автентифікації." +
                             " Запити на відправлення створюють реальні експрес-накладні, " +
                             "якщо активовано функцію відправлення через «Нову пошту». в пропертях"));
    }
}
