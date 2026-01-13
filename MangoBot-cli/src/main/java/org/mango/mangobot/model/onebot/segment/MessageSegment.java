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
    @JsonSubTypes.Type(value = MarkdownSegment.class, name = "markdown"),
    @JsonSubTypes.Type(value = VideoSegment.class, name = "video"),
    @JsonSubTypes.Type(value = RecordSegment.class, name = "record"),
    @JsonSubTypes.Type(value = RpsSegment.class, name = "rps"),
    @JsonSubTypes.Type(value = DiceSegment.class, name = "dice"),
    @JsonSubTypes.Type(value = ShakeSegment.class, name = "shake"),
    @JsonSubTypes.Type(value = PokeSegment.class, name = "poke"),
    @JsonSubTypes.Type(value = AnonymousSegment.class, name = "anonymous"),
    @JsonSubTypes.Type(value = ShareSegment.class, name = "share"),
    @JsonSubTypes.Type(value = ContactSegment.class, name = "contact"),
    @JsonSubTypes.Type(value = LocationSegment.class, name = "location"),
    @JsonSubTypes.Type(value = MusicSegment.class, name = "music"),
    @JsonSubTypes.Type(value = NodeSegment.class, name = "node"),
    @JsonSubTypes.Type(value = XmlSegment.class, name = "xml"),
    @JsonSubTypes.Type(value = ForwardSegment.class, name = "forward")
})
@Data
public abstract class MessageSegment {
    private String type;
}
