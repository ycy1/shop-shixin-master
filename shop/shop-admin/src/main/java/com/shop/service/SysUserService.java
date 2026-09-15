package com.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shop.dto.user.SysUserAddAndUpdateDto;
import com.shop.entity.SysUser;
import com.shop.dto.user.UpdatePwdDTO;
import com.shop.vo.user.OnlineUserVo;
import com.shop.vo.user.SysUserVo;
import com.shop.vo.user.SysUserProfileVo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.http.HttpResponse;
import java.util.List;

public interface SysUserService extends IService<SysUser> {
    /**
     * 分页查询用户
     */
    IPage<SysUserVo> listUsers(SysUser sysUser);

    /**
     * 用户导出
     */
    ResponseEntity<byte[]> export(SysUser sysUser);

    /**
     * 用户导入
     */
    Integer importUsers(MultipartFile file);

    /**
     * 新增用户
     */
    void add(SysUserAddAndUpdateDto user);

    /**
     * 更新用户
     */
    void update(SysUserAddAndUpdateDto user);

    /**
     * 删除用户
     */
    void delete(List<Integer> ids);


    /**
     * 修改密码
     *
     * @param updatePwdDTO 修改密码参数
     */
    void updatePwd(UpdatePwdDTO updatePwdDTO);

    /**
     * 生成用户二维码并更新 qr_img
     *
     * @param userId 用户id
     * @return 二维码图片地址
     */
    String generateQr(Integer userId);

    /**
     * 获取个人信息
     * @return
     */
    SysUserProfileVo profile();

    /**
     * 修改个人信息
     * @param user
     */
    void updateProfile(SysUser user);

    /**
     * 锁屏界面验证密码
     * @param password
     * @return
     */
    Boolean verifyPassword(String password);

    /**
     * 重置密码
     * @param user
     * @return
     */
    Boolean resetPassword(SysUser user);

    /**
     * 获取在线用户列表
     * @return
     */
    IPage<OnlineUserVo> getOnlineUserList(String username);


}
