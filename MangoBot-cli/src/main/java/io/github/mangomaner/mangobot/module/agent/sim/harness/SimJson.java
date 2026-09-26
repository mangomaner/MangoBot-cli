package io.github.mangomaner.mangobot.module.agent.sim.harness;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模拟台内唯一的 ObjectMapper。
 *
 * <p>容错优先：关掉 {@code FAIL_ON_UNKNOWN_PROPERTIES}。这里是手工驱动、逐条构造帧的地方，
 * 多一个字段不该让整场模拟跑不起来。
 */
final class SimJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private SimJson() {
    }
}
