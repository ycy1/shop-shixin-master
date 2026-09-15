package com.shop.tools;

import com.shop.utils.ToolsResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class MovieTools {

    @Tool("查询具体电影的信息")
    public ToolsResult getMovieInfo(@P("传入电影的名称") String movieName) {
        return ToolsResult.success("《" + movieName + "》是2019年上映的科幻电影，由<周星驰>导演，由<林允>、<邓超>、<张雨绮>主演。");
    }

}