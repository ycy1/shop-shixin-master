package com.shop.api.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.shop.api.service.ShopAccountWriteService;
import com.shop.dto.user.UpdatePwdDTO;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.LoginUserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author xxj
 * @title ShopAccountWriteServiceImpl
 * @description 前台改自己账号上的两件事：密码（判据抄 SysUserServiceImpl#updatePwd）和基本信息
 *              （抄 SysUserServiceImpl#updateMyProfile），两条都去掉「能替别人改」那一档
 */
@Service
@RequiredArgsConstructor
public class ShopAccountWriteServiceImpl implements ShopAccountWriteService {

    private final SysUserMapper sysUserMapper;

    /** 手机号只验形状不查重（用户管理那一路、后台个人信息那一路改手机号也不查重，三条口径一致） */
    private static final String MOBILE_PATTERN = "1[3-9]\\d{9}";

    /**
     * 改的是 token 背后那一个账号 —— 入参里没有 userId 这一格，读库和写库钉的都是登录 id。
     *
     * 旧密码必须比对（和后台那一路同一句「旧密码错误」）：这一条挂在个人中心上，
     * 谁拿着没锁的电脑点开就能改密码，比对一次把「会话被人蹭了」这一档挡住。
     * 写库只交 id + 新密码那一格（半截实体）：全局 field-strategy 是 not_empty，
     * 没填的列不进 UPDATE，所以不会像整实体那样把 status / account_type 这些跟着覆盖一遍。
     */
    @Override
    public void updatePassword(UpdatePwdDTO form) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后修改密码").getId();
        String oldPassword = form == null ? null : form.getOldPassword();
        String newPassword = form == null ? null : form.getNewPassword();
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("请填写旧密码和新密码");
        }

        SysUser user = sysUserMapper.selectById(loginUserId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            // 微信扫码进来的账号那一格是空的：不拦这一下，BCrypt.checkpw 直接抛 NPE 变成 500
            throw new ServiceException("这个账号没有设过密码，请用邮箱验证码重置一个再来改");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new ServiceException("旧密码错误");
        }

        SysUser update = new SysUser();
        update.setId(loginUserId.intValue());
        update.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        if (sysUserMapper.updateById(update) != 1) {
            throw new ServiceException("修改密码失败");
        }
    }

    /**
     * 改的是 token 背后那一个账号 —— id 由服务端钉，参数里没有 userId 这一格。
     *
     * 只认那几格、逐格搬（和后台 updateMyProfile 同一句理由）：这一颗按钮不压权限，
     * 原样 updateById(form) 的话请求体里塞一个 accountType=0 就把自己提成平台账号、status 一填就把自己停用。
     * 头像这一格送的是已经落在存储上的 URL（图是前台先走 /api/file/uploadImage 传上去拿回来的），
     * 这一条只认那一串地址，不收文件、也不管那是哪来的桶。
     *
     * 空值不写：全局 field-strategy=not_empty，所以这几格改得了、清不掉（这张表的 area_code / 经纬度
     * 三列本来就是这个脾气，实体上那两行注释交代过），前端表单里提醒一句。
     */
    @Override
    public void updateProfile(SysUser form) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后修改资料").getId();
        if (form == null) {
            throw new ServiceException("没有要修改的内容");
        }
        String nickname = form.getNickname();
        if (nickname == null || nickname.isBlank()) {
            throw new ServiceException("昵称不能为空");
        }
        String mobile = form.getMobile();
        if (mobile != null && !mobile.isBlank() && !mobile.matches(MOBILE_PATTERN)) {
            throw new ServiceException("手机号格式不正确");
        }

        SysUser update = new SysUser();
        update.setId(loginUserId.intValue());
        update.setNickname(nickname.trim());
        update.setMobile(mobile);
        update.setEmail(form.getEmail());
        update.setSex(form.getSex());
        update.setAvatar(form.getAvatar());
        update.setAreaCode(form.getAreaCode());
        update.setAreaZh(form.getAreaZh());
        update.setAddress(form.getAddress());
        update.setLongitude(form.getLongitude());
        update.setLatitude(form.getLatitude());
        update.setSignature(form.getSignature());
        if (sysUserMapper.updateById(update) != 1) {
            throw new ServiceException("修改失败");
        }
    }
}
