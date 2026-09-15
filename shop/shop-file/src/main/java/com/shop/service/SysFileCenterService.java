package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.dto.file.SysFileCenterQueryDto;
import com.shop.vo.file.SysFileCenterVo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件中心 Service接口
 */
public interface SysFileCenterService {

    /**
     * 分页查询（按 source 作用域：我的/部门/角色/回收站）
     */
    IPage<SysFileCenterVo> page(SysFileCenterQueryDto dto);

    /**
     * 上传：先插 sys_file_center（处理中），再调用 file 模块 fastdfs 上传，最后更新记录
     */
    SysFileCenterVo upload(MultipartFile file, String businessType, Integer fileSource);

    /**
     * 下载（校验可访问后下载，下载次数+1）
     */
    ResponseEntity<byte[]> download(Long id);

    /**
     * 移入回收站（file_source=4）
     */
    void softDelete(List<Long> ids);

    /**
     * 还原（file_source=1，回到我的文件）
     */
    void restore(List<Long> ids);

    /**
     * 彻底删除（删除记录 + 删除存储文件）
     */
    void deleteForever(List<Long> ids);

    /**
     * 设置过期时间（单个/批量）
     */
    void updateExpireTime(List<Long> ids, LocalDateTime expireTime);
}
