package io.github.mangomaner.mangobot.adapter.onebot.model.segment;

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
    @JsonSubTypes.Type(value = MarkdownSegment.class, name = "markdown"),
    @JsonSubTypes.Type(value = VideoSegment.class, name = "video"),
    @JsonSubTypes.Type(value = RecordSegment.class, name = "record"),
    @JsonSubTypes.Type(value = ShakeSegment.class, name = "shake"),
    @JsonSubTypes.Type(value = ShareSegment.class, name = "share"),
    @JsonSubTypes.Type(value = NodeSegment.class, name = "node"),
    @JsonSubTypes.Type(value = ForwardSegment.class, name = "forward"),
    @JsonSubTypes.Type(value = PokeSegment.class, name = "poke"),
    @JsonSubTypes.Type(value = LocationSegment.class, name = "location")
})
@Data
public abstract class OneBotMessageSegment {
    private String type;
}