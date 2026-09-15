package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.Constants;
import com.shop.dto.file.SysFileCenterQueryDto;
import com.shop.entity.SysFileCenter;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysFileCenterMapper;
import com.shop.service.SysFileCenterService;
import com.shop.utils.DateUtil;
import com.shop.utils.PageUtil;
import com.shop.vo.file.SysFileCenterVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileCenterServiceImpl extends ServiceImpl<SysFileCenterMapper, SysFileCenter> implements SysFileCenterService {

    private final FileStorageService fileStorageService;
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(10);

    @Override
    public IPage<SysFileCenterVo> page(SysFileCenterQueryDto dto) {
        if (dto.getSource() == null) {
            dto.setSource(1);
        }
        dto.setUserId(StpUtil.getLoginIdAsLong());
        dto.setAdmin(StpUtil.hasRole(Constants.ADMIN));
        return baseMapper.selectFileCenterPage(PageUtil.getPage(), dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFileCenterVo upload(MultipartFile file, String businessType, Integer fileSource) {
        Integer userId = StpUtil.getLoginIdAsInt();
        String fileId = UUID.randomUUID().toString().replace("-", "");
        // 1. 先插入记录（处理中）
        SysFileCenter fc = new SysFileCenter();
        fc.setFileId(fileId);
        fc.setUserId(userId.longValue());
        fc.setBusinessType(businessType);
        fc.setFileName(file.getOriginalFilename());
        fc.setFileSource(fileSource == null ? 1 : fileSource);
        fc.setOriginalSource(fc.getFileSource());
        fc.setFileStatus(1); // 处理中
        fc.setDownloadCount(0);
        save(fc);

        // 2. 异步调用 file 模块 fastdfs 上传到服务器，返回 url
        taskExecutor.submit(() -> {
            String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/filecenter/";
            FileInfo fileInfo;
            try {
                fileInfo = fileStorageService.of(file)
                        .setPlatform(fileStorageService.getProperties().getDefaultPlatform())
                        .setPath(path)
                        .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + file.getOriginalFilename())
                        .setObjectId(fileId)
                        .setObjectType("filecenter")
                        .putAttr("source", "filecenter")
                        .upload();
            } catch (Exception e) {
                log.error("文件中心上传失败:{}", e.getMessage());
                fc.setFileStatus(3); // 上传失败
                fc.setFailReason(e.getMessage());
                updateById(fc);
                throw new ServiceException("上传文件失败: " + e.getMessage());
            }
            if (fileInfo == null) {
                throw new ServiceException("上传文件失败");
            }

            // 3. 更新记录：url / 大小 / 状态 / 过期时间（7天后过期）
            fc.setFileUrl(fileInfo.getUrl());
            fc.setFileSize(fileInfo.getSize());
            fc.setFileStatus(2); // 处理完成
            fc.setExpireTime(LocalDateTime.now().plusDays(7));
            updateById(fc);
        });
        return BeanUtil.copyProperties(fc, SysFileCenterVo.class);
    }

    @Override
    public ResponseEntity<byte[]> download(Long id) {
        SysFileCenter fc = getById(id);
        if (fc == null) {
            throw new ServiceException("文件不存在");
        }
        if (!canView(fc)) {
            throw new ServiceException("无权限访问该文件");
        }
        byte[] bytes;
        try {
            bytes = fileStorageService.download(fc.getFileUrl()).bytes();
        } catch (Exception e) {
            log.error("文件下载失败:{}", e.getMessage());
            throw new ServiceException("文件下载失败: " + e.getMessage());
        }
        // 下载次数 +1
        fc.setDownloadCount((fc.getDownloadCount() == null ? 0 : fc.getDownloadCount()) + 1);
        updateById(fc);

        HttpHeaders headers = new HttpHeaders();
        String fileName = fc.getFileName() == null ? "file" : fc.getFileName();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(List<Long> ids) {
        for (Long id : ids) {
            SysFileCenter fc = getById(id);
            if (fc == null) continue;
            checkOwnerOrAdmin(fc);
            // 记录删除前的来源，便于还原
            if (fc.getOriginalSource() == null) {
                fc.setOriginalSource(fc.getFileSource());
            }
            fc.setFileSource(4); // 删除
            fc.setFileStatus(4); // 已过期/已清理
            updateById(fc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(List<Long> ids) {
        for (Long id : ids) {
            SysFileCenter fc = getById(id);
            if (fc == null || fc.getFileSource() != 4 || fc.getFileStatus() == 4) continue;
            if (!canView(fc)) {
                throw new ServiceException("无权限还原该文件");
            }
            // 还原到删除前的来源（部门/角色），无记录时回「我的文件」
            fc.setFileSource(fc.getOriginalSource() != null ? fc.getOriginalSource() : 1);
            fc.setOriginalSource(null);
            // 还原后重新激活：状态置为已完成，刷新过期时间
            fc.setFileStatus(2); // 处理完成
            fc.setExpireTime(LocalDateTime.now().plusDays(7));
            updateById(fc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForever(List<Long> ids) {
        for (Long id : ids) {
            SysFileCenter fc = getById(id);
            if (fc == null) continue;
            checkOwnerOrAdmin(fc);
            try {
                fileStorageService.delete(fc.getFileUrl());
            } catch (Exception e) {
                log.warn("删除存储文件失败:{}", e.getMessage());
            }
            removeById(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExpireTime(List<Long> ids, LocalDateTime expireTime) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请选择要设置的文件");
        }
        if (expireTime == null) {
            throw new ServiceException("过期时间不能为空");
        }
        for (Long id : ids) {
            SysFileCenter fc = getById(id);
            if (fc == null) continue;
            checkOwnerOrAdmin(fc);
            fc.setExpireTime(expireTime);
            updateById(fc);
        }
    }

    /**
     * 是否可访问：管理员、本人、同部门或同角色可见
     */
    private boolean canView(SysFileCenter fc) {
        if (StpUtil.hasRole(Constants.ADMIN)) return true;
        if (fc.getUserId() != null && fc.getUserId().intValue() == StpUtil.getLoginIdAsInt()) return true;
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return baseMapper.countSharedDept(fc.getUserId(), currentUserId) > 0
                || baseMapper.countSharedRole(fc.getUserId(), currentUserId) > 0;
    }

    /**
     * 仅本人或管理员可操作
     */
    private void checkOwnerOrAdmin(SysFileCenter fc) {
        if (StpUtil.hasRole(Constants.ADMIN)) return;
        if (fc.getUserId() == null || fc.getUserId().intValue() != StpUtil.getLoginIdAsInt()) {
            throw new ServiceException("只能操作自己的文件");
        }
    }
}
