package com.shop.tools;

import com.shop.utils.ToolsResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class WeatherTools {

    @Tool("返回给定城市的天气预报")
    public ToolsResult getWeather(@P("应返回天气预报的城市") String city) {
        return ToolsResult.success("今天"+city+"的天气会下雨");
    }

    @Tool("查询函数，返回搜索到的信息")
    public ToolsResult getSeacher(@P("传入需要查询的问题") String query) {
        return ToolsResult.success("搜索："+query);
    }

    @Tool("查询具体城市的机票信息")
    public ToolsResult getAirTicket(@P("传入出发地") String city, @P("传入目的地") String city2) {
        return ToolsResult.success("从" + city + "到" + city2 + "的机票有2张");
    }


}
