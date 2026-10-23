package com.shop.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.dto.user.SysUserAddAndUpdateDto;
import com.shop.common.Result;
import com.shop.dto.user.UpdatePwdDTO;
import com.shop.entity.SysUser;
import com.shop.service.SysUserService;
import com.shop.vo.user.SysUserVo;
import com.shop.vo.user.SysUserProfileVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
@Api(tags = "用户管理")
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    @ApiOperation(value = "获取用户列表")
    public Result<IPage<SysUserVo>> listUsers(SysUser sysUser) {
        return Result.success(sysUserService.listUsers(sysUser));
    }

    @GetMapping("/exportUsers")
    @ApiOperation(value = "用户导出")
    @SaCheckPermission("sys:user:export")
    public ResponseEntity<byte[]> exportUsers(SysUser sysUser) {
        return sysUserService.export(sysUser);
    }

    @PostMapping("/importUser")
    @ApiOperation(value = "用户导入")
    @SaCheckPermission("sys:user:import")
    public Result<Integer> importUser(@RequestParam("file") MultipartFile file) {
        return Result.success(sysUserService.importUsers(file));
    }

    @PostMapping
    @OperationLogger("新增用户")
    @ApiOperation(value = "新增用户")
    @SaCheckPermission("sys:user:add")
    public Result<Void> addUser(@RequestBody SysUserAddAndUpdateDto sysUserAddDto) {
        sysUserService.add(sysUserAddDto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户信息")
    public Result<SysUser> getUser(@PathVariable Integer id) {
        return Result.success(sysUserService.getById(id));
    }

    @PutMapping
    @OperationLogger("修改用户")
    @ApiOperation(value = "修改用户")
    @SaCheckPermission("sys:user:update")
    public Result<Void> update(@RequestBody SysUserAddAndUpdateDto user) {
        sysUserService.update(user);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @OperationLogger("批量删除用户")
    @ApiOperation(value = "批量删除用户")
    @SaCheckPermission("sys:user:delete")
    public Result<Void> delete(@PathVariable List<Integer> ids) {
        sysUserService.delete(ids);
        return Result.success();
    }

    @PutMapping("/updatePwd")
    @ApiOperation(value = "修改密码")
    @SaCheckPermission("sys:user:updatePwd")
    public Result<Void> updatePwd(@RequestBody UpdatePwdDTO updatePwdDTO) {
        sysUserService.updatePwd(updatePwdDTO);
        return Result.success();
    }

    @PostMapping("/generateQr/{id}")
    @ApiOperation(value = "生成用户二维码")
    @SaCheckPermission("sys:user:update")
    public Result<String> generateQr(@PathVariable Integer id) {
        return Result.success(sysUserService.generateQr(id));
    }

    @GetMapping("/profile")
    @ApiOperation(value = "获取个人信息")
    public Result<SysUserProfileVo> profile() {
        return Result.success(sysUserService.profile());
    }

    @PutMapping("/updProfile")
    @OperationLogger("修改个人信息")
    @ApiOperation(value = "修改个人信息")
    @SaCheckPermission("sys:user:update")
    public Result<SysUserProfileVo> updateProfile(@RequestBody SysUser user) {
        sysUserService.updateProfile(user);
        return Result.success();
    }

    /**
     * 改自己的基本信息（后台「个人信息」那一页那颗「修改」）
     *
     * 不压 sys:user:update —— 那条是用户管理的修改权限，给每个账号勾上就等于能改别人的档案；
     * 这一条能免权限的前提是服务端把字段收窄到 昵称/手机号/邮箱/性别/头像/地区/详细地址/经纬度/签名
     * 且 id 钉成登录 id（见 SysUserServiceImpl#updateMyProfile），传什么都不越出自己那一行。
     */
    @PutMapping("/myProfile")
    @OperationLogger("修改个人信息")
    @ApiOperation(value = "修改自己的基本信息")
    public Result<Void> updateMyProfile(@RequestBody SysUser user) {
        sysUserService.updateMyProfile(user);
        return Result.success();
    }


    @GetMapping("/verifyPassword/{password}")
    @ApiOperation(value = "锁屏界面验证密码")
    public Result<Boolean> verifyPassword(@PathVariable String password) {
        return Result.success(sysUserService.verifyPassword(password));
    }

    @PutMapping("/reset")
    @OperationLogger("重置密码")
    @ApiOperation(value = "重置密码")
    @SaCheckPermission("sys:user:reset")
    public Result<Boolean> resetPassword(@RequestBody SysUser user) {
        return Result.success(sysUserService.resetPassword(user));
    }
}
