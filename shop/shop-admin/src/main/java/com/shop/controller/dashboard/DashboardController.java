package com.shop.controller.dashboard;

import com.shop.common.Result;
import com.shop.service.IndexService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sys/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IndexService indexService;

    @GetMapping
    @ApiOperation(value = "首页统计数据")
    public Result<Map<String, Object>> index() {
        return Result.success(indexService.index());
    }

}
