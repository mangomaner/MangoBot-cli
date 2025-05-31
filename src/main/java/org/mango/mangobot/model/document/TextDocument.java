package org.mango.mangobot.model.document;

import lombok.Data;

@Data
public class TextDocument {
    private String id;
    private String content;
    private float[] vectorEmbedding;
}