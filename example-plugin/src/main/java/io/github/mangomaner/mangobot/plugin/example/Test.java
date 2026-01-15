package io.github.mangomaner.mangobot.plugin.example;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class Test {
    private int abc;
    public void test() {
        System.out.println("test");
    }
}
