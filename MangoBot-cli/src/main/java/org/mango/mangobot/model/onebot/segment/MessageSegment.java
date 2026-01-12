package org.mango.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextSegment.class, name = "text"),
    @JsonSubTypes.Type(value = ImageSegment.class, name = "image"),
    @JsonSubTypes.Type(value = AtSegment.class, name = "at"),
    @JsonSubTypes.Type(value = ReplySegment.class, name = "reply"),
    @JsonSubTypes.Type(value = FaceSegment.class, name = "face"),
    @JsonSubTypes.Type(value = JsonSegment.class, name = "json"),
    @JsonSubTypes.Type(value = FileSegment.class, name = "file"),
    @JsonSubTypes.Type(value = KeyboardSegment.class, name = "keyboard"),
    @JsonSubTypes.Type(value = MarkdownSegment.class, name = "markdown")
})
@Data
public abstract class MessageSegment {
    private String type;
}
