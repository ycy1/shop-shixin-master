package com.shop.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.common.Result;
import com.shop.dto.file.SysFileCenterExpireDto;
import com.shop.dto.file.SysFileCenterQueryDto;
import com.shop.service.SysFileCenterService;
import com.shop.vo.file.SysFileCenterVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件中心
 */
@RestController
@RequestMapping("/file/center")
@Api(tags = "文件中心")
@RequiredArgsConstructor
public class SysFileCenterController {

    private final SysFileCenterService sysFileCenterService;

    @SaCheckLogin
    @GetMapping("/page")
    @ApiOperation(value = "文件中心分页列表")
    public Result<IPage<SysFileCenterVo>> page(SysFileCenterQueryDto dto) {
        return Result.success(sysFileCenterService.page(dto));
    }

    @SaCheckLogin
    @PostMapping("/upload")
    @SaCheckPermission("sys:filecenter:upload")
    @ApiOperation(value = "上传文件")
    public Result<SysFileCenterVo> upload(@RequestParam("file") MultipartFile file, String businessType, Integer fileSource) {
        return Result.success(sysFileCenterService.upload(file, businessType, fileSource));
    }

    @SaCheckLogin
    @GetMapping("/download/{id}")
    @SaCheckPermission("sys:filecenter:download")
    @ApiOperation(value = "下载文件")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) {
        return sysFileCenterService.download(id);
    }

    @SaCheckLogin
    @DeleteMapping("/{ids}")
    @SaCheckPermission("sys:filecenter:delete")
    @ApiOperation(value = "移入回收站")
    public Result<Void> delete(@PathVariable("ids") List<Long> ids) {
        sysFileCenterService.softDelete(ids);
        return Result.success();
    }

    @SaCheckLogin
    @PutMapping("/restore/{ids}")
    @SaCheckPermission("sys:filecenter:delete")
    @ApiOperation(value = "还原文件")
    public Result<Void> restore(@PathVariable("ids") List<Long> ids) {
        sysFileCenterService.restore(ids);
        return Result.success();
    }

    @SaCheckLogin
    @PutMapping("/expire")
    @SaCheckPermission("sys:filecenter:expire")
    @ApiOperation(value = "设置文件过期时间")
    public Result<Void> updateExpireTime(@RequestBody SysFileCenterExpireDto dto) {
        sysFileCenterService.updateExpireTime(dto.getIds(), dto.getExpireTime());
        return Result.success();
    }

    @SaCheckLogin
    @DeleteMapping("/forever/{ids}")
    @SaCheckPermission("sys:filecenter:delete")
    @ApiOperation(value = "彻底删除文件")
    public Result<Void> deleteForever(@PathVariable("ids") List<Long> ids) {
        sysFileCenterService.deleteForever(ids);
        return Result.success();
    }
}
