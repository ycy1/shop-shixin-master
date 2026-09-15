package com.shop.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.shop.common.Result;
import com.shop.exception.ServiceException;
import com.shop.service.FileDetailService;
import com.shop.utils.DateUtil;
import com.shop.utils.FileUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @author xxj
 * @title FileApiController
 * @date 2025/8/27 15:17
 * @description TODO
 */
@RestController
@RequestMapping("/api/file")
@Api(tags = "文件管理")
@RequiredArgsConstructor
@Slf4j
public class FileApiController {
    private final FileDetailService fileDetailService;

    private final FileStorageService fileStorageService;

    @SaCheckLogin
    @PostMapping("/uploadImage")
    @ApiOperation(value = "上传图片")
    public Result<String> uploadImage(MultipartFile file, String source) {
        String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/";
        //这个source可在前端上传文件时提供，可用来区分是头像还是文章图片等
        if (StringUtils.isNotBlank(source)) {
            path = path + source + "/";
        }
        String defaultPlatform = fileStorageService.getProperties().getDefaultPlatform();
        //获取文件名和后缀
        File compressFile = FileUtils.compressFile(file);
        FileInfo fileInfo = fileStorageService.of(compressFile)
                .setPlatform(defaultPlatform)
                .setPath(path)
                .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + file.getOriginalFilename()) //随机俩个数字，避免相同文件名时文件名冲突
                .putAttr("source",source)
                .upload();

        if (fileInfo == null) {
            throw new ServiceException("上传文件失败");
        }
        log.info("默认存储平台：{},{}",defaultPlatform,fileInfo.getUrl());
//        FileUtil.del(compressFile);
        return Result.success(fileInfo.getUrl());
    }

    @SaCheckLogin
    @PutMapping("/uploadUrl")
    @ApiOperation(value = "上传文件")
    public Result<String> uploadUrl(String url, String source) {
        String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/";
        //这个source可在前端上传文件时提供，可用来区分是头像还是文章图片等
        if (StringUtils.isNotBlank(source)) {
            path = path + source + "/";
        }
        String defaultPlatform = fileStorageService.getProperties().getDefaultPlatform();

        FileInfo fileInfo = fileStorageService.of(url)
                .setPlatform(defaultPlatform)
                .setPath(path)
                .setOriginalFilename(FileUtil.getName(url)+".webp")
                .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + FileUtil.getName(url)) //随机俩个数字，避免相同文件名时文件名冲突
                .putAttr("source",source)
                .upload();
        if (fileInfo == null) {
            throw new ServiceException("上传文件失败");
        }
        log.info("默认存储平台：{},{}",defaultPlatform,fileInfo.getUrl());
        return Result.success(fileInfo.getUrl());
    }

}
