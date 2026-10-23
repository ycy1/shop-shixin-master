package com.shop.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.Constants;
import com.shop.common.RedisConstants;
import com.shop.config.fesod.CustomCellStyleWriteHandler;
import com.shop.config.fesod.CustomReadDataListener;
import com.shop.dto.user.SysUserAddAndUpdateDto;
import com.shop.export.SysUserExport;
import com.shop.mapper.SysDeptUserMapper;
import com.shop.mapper.SysRoleMapper;
import com.shop.service.SysFileCenterService;
import com.shop.utils.*;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysUserMapper;
import com.shop.service.SysUserService;
import com.shop.vo.user.OnlineUserVo;
import com.shop.vo.user.SysUserVo;
import com.shop.vo.user.SysUserProfileVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.fesod.sheet.FesodSheet;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;

import java.io.*;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.shop.dto.user.UpdatePwdDTO;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleMapper roleMapper;
    private final RedisUtil redisUtil;
    private final SysUserMapper sysUserMapper;
    private final FileStorageService fileStorageService;
    private final SysDeptUserMapper deptUserMapper;
    private final SysFileCenterService fileCenterService;

    @Override
    public IPage<SysUserVo> listUsers(SysUser sysUser) {
        // 部门信息已由 selectUserPage 在 SQL 层聚合填充（deptIds/deptNames）
        return baseMapper.selectUserPage(PageUtil.getPage(),sysUser);
    }

    @Override
    public ResponseEntity<byte[]> export(SysUser sysUser) {
        Page<SysUserVo> page = PageUtil.getPage();
        // 勾选导出：按勾选数量一次取回，避免被默认 pageSize(10) 截断
        if (sysUser.getIds() != null && !sysUser.getIds().isEmpty()) {
            page.setCurrent(1);
            page.setSize(sysUser.getIds().size());
        }
        IPage<SysUserVo> sysUserVoIPage = baseMapper.selectUserPage(page, sysUser);
        List<SysUserExport> sysUserExports = BeanUtil.copyToList(sysUserVoIPage.getRecords(), SysUserExport.class);
        for (SysUserExport sysUserExport : sysUserExports) {
            sysUserExport.setQrImgFile(FileUtils.urlToFile(sysUserExport.getQrImg()));
        }


        HttpHeaders headers = new HttpHeaders();
        byte[] byteArray = null;
        try {
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            FesodSheet.write(bao, SysUserExport.class)
                    .registerWriteHandler(new CustomCellStyleWriteHandler())
                    .sheet("模板").doWrite(sysUserExports);

            byteArray = bao.toByteArray();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + URLEncoder.encode("用户数据_" + DateUtil.parseDateToStr(DateUtil.YYYYMMDDHHMMSS, DateUtil.getNowDate()) + ".xlsx", StandardCharsets.UTF_8));
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(byteArray.length);
        } catch (Exception e) {
            log.error("导出用户信息失败:{}", e.getMessage());
            throw new RuntimeException("导出用户信息失败: " + e.getMessage());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(byteArray);

    }

    @Override
    public Integer importUsers(MultipartFile file) {
        CustomReadDataListener<SysUserExport> dataListener = null;
        dataListener = new CustomReadDataListener<>("users");
        List<SysUserExport> users = new ArrayList<>();
        try {
            FesodSheet.read(file.getInputStream(), SysUserExport.class, dataListener).sheet().doRead();
            users = dataListener.getDatas("users");
        } catch (Exception e) {
            log.error("导入用户信息失败:{}", e.getMessage());
            throw new RuntimeException("导入用户信息失败: " + e.getMessage());
        }

        return users.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(SysUserAddAndUpdateDto SysUserAddAndUpdateDto) {
        // 检查用户名是否已存在
        SysUser user = SysUserAddAndUpdateDto.getUser();
        if (baseMapper.selectByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(),BCrypt.gensalt()));
        save(user);

        //保存部门信息
        List<Long> deptIds = SysUserAddAndUpdateDto.getDeptIds();
        if (deptIds != null && !deptIds.isEmpty()) {
            deptUserMapper.insertDeptUser(user.getId(), deptIds.stream().distinct().collect(Collectors.toList()));
        }

        //保存角色信息
        if(SysUserAddAndUpdateDto.getRoleIds().isEmpty()) return;
        roleMapper.addRoleUser(user.getId(), SysUserAddAndUpdateDto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserAddAndUpdateDto user) {
        // 检查用户是否存在
        if (getById(user.getUser().getId()) == null) {
            throw new RuntimeException("用户不存在");
        }
        updateById(user.getUser());

        //修改部门 先删除再新增（空列表也执行删除，用于清除原有部门）
        deptUserMapper.deleteByUserIds(Collections.singletonList(user.getUser().getId()));
        List<Long> deptIds = user.getDeptIds();
        if (deptIds != null && !deptIds.isEmpty()) {
            deptUserMapper.insertDeptUser(user.getUser().getId(), deptIds.stream().distinct().collect(Collectors.toList()));
        }

        //修改角色 先删除角色再新增
        if(user.getRoleIds().isEmpty()) return;
        roleMapper.deleteRoleByUserId(Collections.singletonList(user.getUser().getId()));
        roleMapper.addRoleUser(user.getUser().getId(), user.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> ids) {
        removeBatchByIds(ids);
        roleMapper.deleteRoleByUserId(ids);
        if (ids != null && !ids.isEmpty()) {
            deptUserMapper.deleteByUserIds(ids);
        }
    }


    @Override
    public void updatePwd(UpdatePwdDTO updatePwdDTO) {

        SysUser user = this.getById(StpUtil.getLoginIdAsInt());
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        if(!StpUtil.hasRole(Constants.ADMIN) && user.getId() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("只能修改自己的密码！");
        }

        if (!BCrypt.checkpw(updatePwdDTO.getOldPassword(), user.getPassword())) {
            throw new ServiceException("旧密码错误");
        }

        user.setPassword(BCrypt.hashpw(updatePwdDTO.getNewPassword(),BCrypt.gensalt()));
        this.updateById(user);
    }

    @Override
    public SysUserProfileVo profile() {

        SysUser sysUser = baseMapper.selectById(StpUtil.getLoginIdAsInt());
        sysUser.setPassword(null);
        //获取角色
        List<String> roles = roleMapper.selectRolesByUserId(sysUser.getId());

        return SysUserProfileVo.builder().sysUser(sysUser).roles(roles).build();
    }

    @Override
    public void updateProfile(SysUser user) {
        baseMapper.updateById(user);
    }

    /**
     * 改自己名下的基本信息（后台「消费者管理 → 个人信息」那一页的「修改」）
     *
     * 和上面那条 updateProfile 的分别是**这一条逐格搬、id 钉死登录 id**：它不压 sys:user:update
     *（每个账号都得能改自己的昵称），所以拿 SysUser 整个实体当入参时不能原样 updateById ——
     * 请求体里塞一个 accountType=0 就能把自己提成平台用户、status 一填就能把自己停用，
     * 而列表那些读接口是按账号类型分数据范围的 —— 这一层就白分了。
     * 搬过去的只有这几格：昵称 / 手机号 / 邮箱 / 性别 / 头像 / 所在地区 / 详细地址 / 经纬度 / 个性签名。
     *
     * 手机号只验形状不查重（用户管理那一路改手机号也不查重，两条口径一致）。
     * 空值不写：全局 field-strategy=not_empty（SysUser 上经纬度那两列的注释交代过这个脾气），
     * 所以这几格能改不能清空 —— 和右上角「个人中心」那一路同一个表现。
     */
    @Override
    public void updateMyProfile(SysUser form) {
        if (form == null) {
            throw new ServiceException("没有要修改的内容");
        }
        if (StringUtils.isBlank(form.getNickname())) {
            throw new ServiceException("昵称不能为空");
        }
        if (StringUtils.isNotBlank(form.getMobile()) && !form.getMobile().matches("1[3-9]\\d{9}")) {
            throw new ServiceException("手机号格式不正确");
        }
        SysUser user = new SysUser();
        user.setId(StpUtil.getLoginIdAsInt());
        user.setNickname(form.getNickname().trim());
        user.setMobile(form.getMobile());
        user.setEmail(form.getEmail());
        user.setSex(form.getSex());
        user.setAvatar(form.getAvatar());
        user.setAreaCode(form.getAreaCode());
        user.setAreaZh(form.getAreaZh());
        user.setAddress(form.getAddress());
        user.setLongitude(form.getLongitude());
        user.setLatitude(form.getLatitude());
        user.setSignature(form.getSignature());
        baseMapper.updateById(user);
    }

    @Override
    public Boolean verifyPassword(String password) {
        SysUser user = baseMapper.selectById(StpUtil.getLoginIdAsInt());
        return BCrypt.checkpw(password, user.getPassword());
    }

    @Override
    public Boolean resetPassword(SysUser user) {
        user.setPassword(BCrypt.hashpw(user.getPassword(),BCrypt.gensalt()));
        baseMapper.updateById(user);
        return true;
    }

    @Override
    public IPage<OnlineUserVo> getOnlineUserList(String username) {
        Integer pageNum = PageUtil.getPageQuery().getPageNum();
        Integer pageSize = PageUtil.getPageQuery().getPageSize();

        // 返回数据对象
        Collection<String> keys = redisUtil.keys(RedisConstants.LOGIN_TOKEN.concat( "*"));

        List<OnlineUserVo> totalList = new ArrayList<>();
        for (String key : keys) {
            Object userObj = redisUtil.get(key);
            OnlineUserVo onlineUser = JSONUtil.toBean(userObj.toString(), OnlineUserVo.class);
            if (StringUtils.isNotBlank(username)) {
                if (onlineUser.getUsername().contains(username)) {
                    totalList.add(onlineUser);
                }
                continue;
            }
            totalList.add(onlineUser);
        }

        //根据时间排序
        totalList.sort((o1, o2) -> o2.getLastLoginTime().compareTo(o1.getLastLoginTime()));

        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = totalList.size() - fromIndex > pageSize ? fromIndex + pageSize : totalList.size();
        List<OnlineUserVo> records = totalList.subList(fromIndex, toIndex);

        IPage<OnlineUserVo> page = new Page<>(pageNum, pageSize);
        page.setRecords(records);
        page.setTotal(totalList.size());
        return page;
    }

    @Override
    public String generateQr(Integer userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        try {
            // 1. 组装二维码纯文本内容并生成 PNG
            // 地址两段拼一起：地区（优先中文，缺失时回退地区编码）+ 详细地址（街道门牌，地图选点回填的那一列）；
            // 两段都可能没填，拼完为空就整行留空，别打出 "地址：null"
            String area = StringUtils.isNotBlank(user.getAreaZh()) ? user.getAreaZh() : user.getAreaCode();
            String address = (StringUtils.isNotBlank(area) ? area : "")
                    + (StringUtils.isNotBlank(user.getAddress()) ? user.getAddress() : "");
            String content = "用户名称：" + (user.getNickname() == null ? "" : user.getNickname())
                    + "\n账号：" + (user.getUsername() == null ? "" : user.getUsername())
                    + "\n电话：" + (user.getMobile() == null ? "" : user.getMobile())
                    + "\n地址：" + address;
            byte[] pngBytes = QrCodeUtils.generatePng(content, 300);
            // 2. 写入临时文件
            File qrFile = File.createTempFile("qr_" + userId + "_", ".png");
            FileUtil.writeBytes(pngBytes, qrFile);

            MultipartFile mf = new StreamMultipartFile(
                    "file",
                    qrFile.toPath(),
                    Files.probeContentType(qrFile.toPath())   // 可能为 null
            );
            File compressFile = FileUtils.compressFile(mf);
            // 3. 上传到文件存储
            String path = DateUtil.parseDateToStr(DateUtil.YYYYMMDD, DateUtil.getNowDate()) + "/qr/";
            String defaultPlatform = fileStorageService.getProperties().getDefaultPlatform();
            FileInfo fileInfo = fileStorageService.of(compressFile)
                    .setPlatform(defaultPlatform)
                    .setPath(path)
                    .setSaveFilename(RandomUtil.randomNumbers(2) + "_" + userId + "_qr.png")
                    .putAttr("source", "qr")
                    .upload();
            FileUtil.del(qrFile);
            if (fileInfo == null) {
                throw new ServiceException("二维码上传失败");
            }
            // 4. 更新用户 qr_img
            SysUser update = new SysUser();
            update.setId(userId);
            update.setQrImg(fileInfo.getUrl());
            this.updateById(update);
            return fileInfo.getUrl();
        } catch (IOException e) {
            log.error("生成二维码失败", e);
            throw new ServiceException("生成二维码失败");
        }
    }
}
