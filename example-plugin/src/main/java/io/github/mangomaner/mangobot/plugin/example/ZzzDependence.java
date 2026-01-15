package io.github.mangomaner.mangobot.plugin.example;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Component
public class ZzzDependence {

    @Autowired
    private Test test;

    private String zzz;
    public void zzz() {
        test.test();
        System.out.println("zzz");
    }

}
