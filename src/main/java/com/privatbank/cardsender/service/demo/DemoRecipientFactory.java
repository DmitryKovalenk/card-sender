package com.privatbank.cardsender.service.demo;

import com.privatbank.cardsender.domain.Recipient;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DemoRecipientFactory {

    public Recipient create(int index) {
        return new Recipient("Test", "User", String.format(Locale.ROOT, "38067%07d", index),
                "Київ", Integer.toString(index % 100 + 1));
    }
}
