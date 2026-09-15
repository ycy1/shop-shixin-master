package com.shop.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.Result;
import com.shop.entity.ConfigSource;
import com.shop.service.ConfigSourceService;
import com.shop.utils.PageUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;

@RestController
@RequestMapping("/sys/config-source")
@Api(tags = "AI-知识源配置")
@RequiredArgsConstructor
public class ConfigSourceController {

    private final ConfigSourceService configSourceService;

    @GetMapping("/list")
    @ApiOperation(value = "知识源列表")
    public Result<IPage<ConfigSource>> list(@RequestParam(required = false) String sourceType) {
        Page<ConfigSource> page = PageUtil.getPage();
        return Result.success(configSourceService.selectPage(page, sourceType));
    }

    @PostMapping("/add")
    @ApiOperation(value = "新增知识源")
    public Result<Boolean> add(@RequestBody ConfigSource configSource) {
        return Result.success(configSourceService.add(configSource));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改知识源")
    public Result<Boolean> update(@RequestBody ConfigSource configSource) {
        return Result.success(configSourceService.update(configSource));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation(value = "删除知识源")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(configSourceService.delete(id));
    }

    @PostMapping("/test-connection")
    @ApiOperation(value = "测试数据库连接")
    public Result<String> testConnection(@RequestBody ConfigSource config) {
        String url = config.getJdbcUrl();
        String user = config.getDbUsername();
        String pwd = config.getDbPassword();

        if (url == null || url.isEmpty()) {
            return Result.error("JDBC URL 不能为空");
        }

        String driverClass;
        if (url.startsWith("jdbc:mysql:")) driverClass = "com.mysql.cj.jdbc.Driver";
        else if (url.startsWith("jdbc:postgresql:")) driverClass = "org.postgresql.Driver";
        else if (url.startsWith("jdbc:oracle:")) driverClass = "oracle.jdbc.OracleDriver";
        else return Result.error("不支持的数据库类型");

        try (Connection conn = DriverManager.getConnection(url, user, pwd)) {
            return Result.success("连接成功：" + conn.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            return Result.error("连接失败：" + e.getMessage());
        }
    }
}
