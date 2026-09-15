package com.shop.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.common.Constants;
import com.shop.common.Result;
import com.shop.entity.FileDetail;
import com.shop.entity.SysFileOss;
import com.shop.exception.ServiceException;
import com.shop.service.FileDetailService;
import com.shop.utils.DateUtil;
import com.shop.utils.FileUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import static com.shop.utils.DateUtil.YYYYMMDDHHMMSS;

@RestController
@RequestMapping("/file")
@Api(tags = "文件管理")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileDetailService fileDetailService;

    private final FileStorageService fileStorageService;



    @SaCheckLogin
    @GetMapping("/list")
    @ApiOperation(value = "获取文件记录表列表")
    public Result<IPage<FileDetail>> list(FileDetail fileDetail) {
        return Result.success(fileDetailService.selectPage(fileDetail));
    }

    @SaCheckLogin
    @GetMapping("/getOssConfig")
    @ApiOperation(value = "获取存储平台配置")
    public Result<List<SysFileOss>> getOssConfig() {
        return Result.success(fileDetailService.getOssConfig());
    }

    @SaCheckLogin
    @PostMapping("/addOss")
    @SaCheckPermission("sys:oss:submit")
    @ApiOperation(value = "添加存储平台配置")
    public Result<Void> addOss(@RequestBody SysFileOss sysFileOss) {
        fileDetailService.addOss(sysFileOss);
        if (sysFileOss.getIsEnable() == Constants.YES) {
            fileStorageService.getProperties().setDefaultPlatform(sysFileOss.getPlatform());
        }
        return Result.success();
    }

    @SaCheckLogin
    @PutMapping("/updateOss")
    @SaCheckPermission("sys:oss:submit")
    @ApiOperation(value = "修改存储平台配置")
    public Result<Void> updateOss(@RequestBody SysFileOss sysFileOss) {
        fileDetailService.updateOss(sysFileOss);
        if (sysFileOss.getIsEnable() == Constants.YES) {
            fileStorageService.getProperties().setDefaultPlatform(sysFileOss.getPlatform());
        }
        return Result.success();
    }

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
    @PostMapping("/uploadUrl")
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
                .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + FileUtil.getName(url)) //随机俩个数字，避免相同文件名时文件名冲突
                .putAttr("source",source)
                .upload();
        if (fileInfo == null) {
            throw new ServiceException("上传文件失败");
        }
        log.info("默认存储平台：{},{}",defaultPlatform,fileInfo.getUrl());
        return Result.success(fileInfo.getUrl());
    }

    @SaCheckLogin
    @PostMapping("/upload")
    @ApiOperation(value = "上传文件")
    public Result<String> upload(MultipartFile file, String source) {
        String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/";
        //这个source可在前端上传文件时提供，可用来区分是头像还是文章图片等
        if (StringUtils.isNotBlank(source)) {
            path = path + source + "/";
        }
        String defaultPlatform = fileStorageService.getProperties().getDefaultPlatform();

        //获取文件名和后缀
        FileInfo fileInfo = fileStorageService.of(FileUtils.isImage(file)?FileUtils.compressFile(file):file)
                .setPlatform(defaultPlatform)
                .setPath(path)
                .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + file.getOriginalFilename()) //随机俩个数字，避免相同文件名时文件名冲突
                .putAttr("source",source)
                .upload();

        if (fileInfo == null) {
            throw new ServiceException("上传文件失败");
        }
        log.info("默认存储平台：{},{}",defaultPlatform,fileInfo.getUrl());
        return Result.success(fileInfo.getUrl());
    }


    @GetMapping("/delete")
    @ApiOperation(value = "删除文件")
    @SaCheckPermission("sys:file:delete")
    public Result<Boolean> delete(String url) {
        try {
            fileStorageService.delete(url);
        } catch (Exception e) {
//            return Result.error("文件不存在");
            log.error("服务器文件删除异常,{}", e.getMessage());
            fileDetailService.delete(url); //删除数据库记录
        }
        return Result.success();
    }

    @PostMapping("/batchDelete")
    @ApiOperation(value = "删除文件")
    @SaCheckPermission("sys:file:delete")
    public Result<Boolean> batchDelete(@RequestBody List<String> urls) {
        try {
            for (String url : urls) {
                fileStorageService.delete(url);
            }
        } catch (Exception e) {
            log.error("文件删除异常,{}", e.getMessage());
        }
        return Result.success();
    }

    @GetMapping("/download")
    @ApiOperation(value = "下载文件")
    public void download(String url, HttpServletResponse response) {
        try {
            boolean exists = fileStorageService.exists(url);
            if (!exists) {
                throw new ServiceException("文件不存在");
            }
            FileInfo fileInfoByUrl = fileStorageService.getFileInfoByUrl(url);
            Downloader download = fileStorageService.download(url);
            response.setHeader("Content-Disposition", "attachment;filename=" + fileInfoByUrl.getFilename());
            ServletOutputStream outputStream = response.getOutputStream();
            download.outputStream(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            log.error("文件下载异常,{}", e.getMessage());
        }
    }

}
