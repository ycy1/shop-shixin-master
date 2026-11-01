package com.shop.service.impl;

import cn.hutool.core.io.FileUtil;
import com.shop.exception.ServiceException;
import com.shop.service.ParkTicketQrStorage;
import com.shop.utils.DateUtil;
import com.shop.utils.FileUtils;
import com.shop.utils.StreamMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 票面二维码图片的存放实现（出票那一步在 shop-commom，图片存储的本事在这一个模块）
 *
 * 形状照抄后台「生成用户二维码」那一条（SysUserServiceImpl#generateQr）：字节落临时 PNG，
 * 包成 MultipartFile 先过一遍 libwebp 压缩，再把压缩后的文件交给 x-file-storage ——
 * 直接传原始 PNG 那一头在这个存储上是取不到的，所以这一趟不能省。
 * 差别只有落盘的子目录按业务分（park-ticket/日期/TK_xxx.png），一眼能对上是哪张票的票面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkTicketQrStorageImpl implements ParkTicketQrStorage {

    /** 落盘的业务目录段（拼在日期后面，和上传接口那条 source 一路） */
    private static final String SOURCE = "park-ticket";

    private final FileStorageService fileStorageService;

    @Override
    public String uploadPng(byte[] pngBytes, String fileName) {
        File qrFile = null;
        try {
            qrFile = File.createTempFile("park_ticket_qr_", ".png");
            FileUtil.writeBytes(pngBytes, qrFile);
            MultipartFile multipartFile = new StreamMultipartFile(
                    "file", qrFile.toPath(), Files.probeContentType(qrFile.toPath()));
            File compressFile = FileUtils.compressFile(multipartFile);
            if (compressFile == null) {
                throw new ServiceException("票面二维码压缩失败（libwebp 没跑出结果）");
            }
            String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/" + SOURCE + "/";
            FileInfo fileInfo = fileStorageService.of(compressFile)
                    .setPlatform(fileStorageService.getProperties().getDefaultPlatform())
                    .setPath(path)
                    .setSaveFilename(fileName)
                    .putAttr("source", SOURCE)
                    .upload();
            if (fileInfo == null) {
                throw new ServiceException("票面二维码上传失败（文件存储没返回结果）");
            }
            return fileInfo.getUrl();
        } catch (IOException e) {
            throw new ServiceException("票面二维码写入临时文件失败：" + e.getMessage());
        } finally {
            // 上传成败都要把临时 PNG 收掉（出票是批量动作，留着就是一堆散在临时目录里的文件）
            if (qrFile != null) {
                FileUtil.del(qrFile);
            }
        }
    }
}
