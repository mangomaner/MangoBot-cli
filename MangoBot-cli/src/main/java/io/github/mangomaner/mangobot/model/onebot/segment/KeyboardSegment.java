package io.github.mangomaner.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KeyboardSegment extends MessageSegment {
    private KeyboardData data;

    @Data
    public static class KeyboardData {
        private List<Row> rows;
    }

    @Data
    public static class Row {
        private List<Button> buttons;
    }

    @Data
    public static class Button {
        private String id;
        @JsonProperty("render_data")
        private RenderData renderData;
        private Action action;
    }

    @Data
    public static class RenderData {
        private String label;
        @JsonProperty("visited_label")
        private String visitedLabel;
        private int style;
    }

    @Data
    public static class Action {
        private int type;
        private Permission permission;
        @JsonProperty("unsupport_tips")
        private String unsupportTips;
        private String data;
        private boolean reply;
        private boolean enter;
    }

    @Data
    public static class Permission {
        private int type;
        @JsonProperty("specify_role_ids")
        private List<String> specifyRoleIds;
        @JsonProperty("specify_user_ids")
        private List<String> specifyUserIds;
    }
}
