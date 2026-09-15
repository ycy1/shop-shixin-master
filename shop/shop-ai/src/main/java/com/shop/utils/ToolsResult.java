package com.shop.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.Data;

@Data
public class ToolsResult {

    private Integer code;
    private String message;
    private JsonElement data;

    public static ToolsResult success(Object data) {
        ToolsResult result = new ToolsResult();
        JsonElement jsonElement = new Gson().toJsonTree(data);
        result.setCode(200);
        result.setMessage("success");
        result.setData(jsonElement);
        return result;
    }

    public static ToolsResult error(String message) {
        ToolsResult result = new ToolsResult();
        result.setCode(500);
        result.setMessage(message);
        result.setData(null);
        return result;
    }
}
