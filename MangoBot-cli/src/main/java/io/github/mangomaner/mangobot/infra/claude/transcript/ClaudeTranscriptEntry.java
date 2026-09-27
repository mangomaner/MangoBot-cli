package io.github.mangomaner.mangobot.infra.claude.transcript;

import com.fasterxml.jackson.databind.JsonNode;

/** 转录里的一行；{@code index} 是它在文件里的行号，出图时要靠它定位。 */
public record ClaudeTranscriptEntry(int index, String type, JsonNode json) {
}
