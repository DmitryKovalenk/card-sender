package com.privatbank.cardsender.controllers;

import com.privatbank.cardsender.dto.response.DemoDataResponse;
import com.privatbank.cardsender.service.demo.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/demo", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Демо", description = "Наповнює базу даних демонстраційними даними для тестування ендпоінтів")
public class DemoController {

    private final DemoDataService demoData;

    /**
     * Тут ми просто генеруємо данні для демонстрації, щоб не створювати їх вручну.
     * Вони будуть згенеровані у базі даних і можна буде перевірити роботу ендпоінтів.
     * Таким чином, ви пропустите частину flow заявки на картку і відправки її у Нову Пошту, бо це все буде згенеровано.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Генерує демонстраційні дані",
            description = "Створює картки з згенерованими номерами і очікуваними відправками, " +
                          "циклічно перебираючи активні типи карток. "
                          + "Уся партія скасовується, якщо будь-який запис не вдається. "
                          + "Отримувачі синтетичні; якщо шедулер Нової Пошти увіикнений, то він надішле ці заявки.")
    public DemoDataResponse generate(
            @Parameter(
                    description = "Цифра - це кількість записів хочемо згенерувати",
                    schema = @Schema(minimum = "1", maximum = "100")
            )
            @RequestParam(defaultValue = "100") int count) {
        return DemoDataResponse.from(demoData.generate(count));
    }
}
