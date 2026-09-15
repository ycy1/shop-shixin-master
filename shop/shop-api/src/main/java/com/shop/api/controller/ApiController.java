package com.shop.api.controller;

import com.shop.common.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户API示例控制器
 * 可根据业务需求在此模块下添加新的控制器
 */
@RestController
@RequestMapping("/api")
@Api(tags = "门户接口")
public class ApiController {

    @GetMapping("/health")
    @ApiOperation("健康检查")
    public Result health() {
        return Result.success("API module is running");
    }

}
