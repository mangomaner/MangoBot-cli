package io.github.mangomaner.mangobot.adapter.onebot.model.segment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ImageSegment extends OneBotMessageSegment {
    private ImageData data;

    @Data
    public static class ImageData {
        private String file;
        // NapCat 在图片段里发/收的是 camelCase subType（实测真实段：
        // {"file":"x.jpg","subType":1,"url":"...","file_size":"33768"}），而 file_size 却是 snake。
        // 别名留着兼容按 OneBot 文档写 sub_type 的实现。
        @JsonProperty("subType")
        @JsonAlias("sub_type")
        private Integer subType;
        private String url;
        @JsonProperty("file_size")
        private String fileSize;
        private String summary; // NapCat
        private String key;     // NapCat
        @JsonProperty("emoji_id")
        private String emojiId; // NapCat
        @JsonProperty("emoji_package_id")
        private Integer emojiPackageId; // NapCat
    }
}
