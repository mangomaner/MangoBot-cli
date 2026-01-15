package org.mango;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.annotation.web.MangoRequestMethod;

@MangoBot
@MangoBotRequestMapping("/my-plugin")
public class MyPluginController {


    ZzzDependence zzzDependence = new ZzzDependence();

    @MangoBotRequestMapping(value = "/hello", method = MangoRequestMethod.GET)
    public String sayHello() {
        zzzDependence.zzz();
        zzzDependence.setZzz("abc");
        return zzzDependence.getZzz();
    }
    
    @MangoBotRequestMapping(value = "/data", method = MangoRequestMethod.POST)
    public String receiveData() {
        return "Data received";
    }
}