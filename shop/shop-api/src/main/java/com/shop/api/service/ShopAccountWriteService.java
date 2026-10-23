package com.shop.api.service;

import com.shop.dto.user.UpdatePwdDTO;
import com.shop.entity.SysUser;

/**
 * 账号 写入服务（前台「个人中心 → 基础信息」上那两颗：「修改」「修改密码」）
 *
 * 为什么写在 shop-api 而不是复用后台那一份：改密码那一路是 PUT /sys/user/updatePwd，
 * 改资料那一路是 PUT /sys/user/myProfile，两条都不在 /api/** 下面（前台 dev 只代理 /api、
 * 生产 nginx 也只反代 /api），够不着；而且 updatePwd 那条还压在 sys:user:updatePwd 权限上、
 * 允许 ADMIN 替别人改。判据照抄一遍，把「替别人改」那一档去掉。
 *
 * 只认登录 id：两条入参里都没有 userId 这一格，改的就是 token 背后那一个账号，
 * 能点名别人就等于让任何人把别人的密码换掉、把别人的档案改掉。
 */
public interface ShopAccountWriteService {

    /**
     * 改自己的密码：旧密码要当场比对（BCrypt），对不上就拒。
     *
     * @param form 只认 oldPassword / newPassword 两格
     */
    void updatePassword(UpdatePwdDTO form);

    /**
     * 改自己的基本信息：头像 / 昵称 / 手机号 / 邮箱 / 性别 / 所在地区（两列）/ 详细地址 / 经纬度（两列）/ 个性签名。
     * 判据抄后台 SysUserServiceImpl#updateMyProfile，一格不少 —— 头像是前台先走 /api/file/uploadImage
     * 把图传上去、这一条只认回来的那串 URL。
     * 关键是**逐格搬、不原样 updateById(整个实体)**：
     * 这一颗按钮免权限（只要登录就能改自己的昵称），不收窄就等于请求体里塞 accountType=0 能把自己提成平台账号、
     * status 一填能把自己停用，而列表那些读接口是按账号类型分数据范围的 —— 那一层就白分了。
     *
     * @param form 只认上面列的那几格，其余一律不看
     */
    void updateProfile(SysUser form);
}
