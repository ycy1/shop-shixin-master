package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.api.service.ShopAddressWriteService;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopConsumerAddress;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.TransactionUtils;
import com.shop.vo.address.ShopAddressVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收货地址 写入实现（判据抄后台那份 ShopConsumerAddressServiceImpl 的 insert/update/deleteByIds，两处不一致就会各写一套）
 *
 * 和后台那三份只差一件事：userId。后台那一格是表单上挑的消费者（不挑才按账号类型收成登录 id），
 * 前台这一格根本没有 —— 新增时参数里传了 userId 也会被覆盖成登录 id，
 * 修改和删除的归属只拿库里那一行比，否则任何人都能往别人地址簿里加一条、改一条、删一条。
 *
 * 两条判据留着照抄（都只在新增和修改那两路用得上）：
 * 一个账号的第一条地址直接当默认（不然他得进第二次才能设上）；
 * 设默认之前先把这个账号其余的默认清掉（is_default 互斥库上表达不出来，只能服务层保证）。
 * 「清默认」和「落这条」写在一个事务里：只清了别人的默认、自己这条没进去，这个人就一条默认地址都不剩。
 */
@Service
@RequiredArgsConstructor
public class ShopAddressWriteServiceImpl implements ShopAddressWriteService {

    private final ShopConsumerAddressMapper shopConsumerAddressMapper;

    /** 只用来确认这个登录账号还在（地址是给这个人存的，人都没了就没必要落这一行） */
    private final SysUserMapper sysUserMapper;

    private static final int DEFAULT_YES = 1;

    private static final int DEFAULT_NO = 0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAddressVo create(ShopAddressSaveDto form) {
        TransactionUtils.requireActive("新增收货地址");
        Long loginUserId = LoginUserUtils.requireLoginUser("后再新增收货地址").getId();
        if (form == null) {
            throw new ServiceException("请填写收货地址");
        }

        ShopConsumerAddress address = toAddress(form);
        // 新增不接受调用方指定主键，也不认参数里那个 userId：归属只可能是登录 id
        address.setId(null);
        address.setUserId(loginUserId);
        assertRequired(address);
        assertUserExists(loginUserId);

        Long count = shopConsumerAddressMapper.selectCount(new LambdaQueryWrapper<ShopConsumerAddress>()
                .eq(ShopConsumerAddress::getUserId, loginUserId));
        if (count == null || count == 0) {
            address.setIsDefault(DEFAULT_YES);
        } else if (address.getIsDefault() == null) {
            address.setIsDefault(DEFAULT_NO);
        }

        // 新记录还没入库，清默认不用排除自己的 id（这一句和后台那一句同形）
        clearOtherDefault(address.getUserId(), address.getIsDefault(), null);
        shopConsumerAddressMapper.insert(address);
        // 回读走后台详情那条 SQL：昵称/用户名是联表带的，前端列表上那一格要的是同一份形状
        return shopConsumerAddressMapper.selectAddressById(address.getId());
    }

    /**
     * 改自己名下的一条：先按 id 捞出原行、把归属比掉，再按 DTO 覆盖。
     *
     * 原行同时用来兜那四列 NOT NULL / 有默认值的列（局部更新时前端不传就沿用原值，
     * 否则要么被 assertRequired 拦下、要么把默认位写成 0，口径同后台那句）。
     * 可空的那几列（area_code / area_zh / tag / 邮编 / 经纬度）实体上钉的是 IGNORED，
     * 不传就真清成 NULL —— 这是「清空所在地区」唯一走得通的路，别改成 not_empty。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAddressVo update(ShopAddressSaveDto form) {
        TransactionUtils.requireActive("修改收货地址");
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再修改收货地址");
        Long loginUserId = login.getId();
        if (form == null || form.getId() == null) {
            throw new ServiceException("收货地址ID不能为空");
        }
        ShopConsumerAddress exists = shopConsumerAddressMapper.selectById(form.getId());
        // 别人的、已经删掉的、根本没这个 id —— 回同一句「收货地址不存在」：
        // 分档等于让调用方拿 id 一句句试出这一条挂在谁名下（口径同订单详情）
        if (exists == null || !loginUserId.equals(exists.getUserId())) {
            throw new ServiceException("收货地址不存在");
        }

        ShopConsumerAddress address = toAddress(form);
        address.setId(form.getId());
        // 归属钉成登录 id：这一条本来就比自己（上面那句），参数里那个 userId 一律不认
        address.setUserId(loginUserId);
        if (address.getReceiverName() == null) {
            address.setReceiverName(exists.getReceiverName());
        }
        if (address.getReceiverPhone() == null) {
            address.setReceiverPhone(exists.getReceiverPhone());
        }
        if (address.getDetail() == null) {
            address.setDetail(exists.getDetail());
        }
        if (address.getIsDefault() == null) {
            address.setIsDefault(exists.getIsDefault());
        }
        assertRequired(address);

        // 这次要排除自己：把别的行清成非默认，自己这条紧接着写成默认
        clearOtherDefault(address.getUserId(), address.getIsDefault(), address.getId());
        shopConsumerAddressMapper.updateById(address);
        return shopConsumerAddressMapper.selectAddressById(address.getId());
    }

    /**
     * 删自己名下的一条：归属判据和 update 那一句一模一样（别人的 / 已删的 / 没这个 id 都回「收货地址不存在」）。
     *
     * 全局配置钉了 logic-delete-field=isDeleted（实体上不用再加 @TableLogic），deleteById 只是一条
     * UPDATE is_deleted=1，行还在库里；selectById 也自动带 is_deleted=0，所以删第二次同样回「不存在」。
     * 删掉默认那条不自动补选 —— 下单时没默认就让用户自己挑，比在服务层猜一个安全（后台同判据）；
     * 这也意味着这一趟只有一条写，所以不像 create / update 那样挂 @Transactional（那两条要两句一起成才算数）。
     */
    @Override
    public void delete(Long id) {
        Long loginUserId = LoginUserUtils.requireLoginUser("后再删除收货地址").getId();
        if (id == null) {
            throw new ServiceException("收货地址ID不能为空");
        }
        ShopConsumerAddress exists = shopConsumerAddressMapper.selectById(id);
        if (exists == null || !loginUserId.equals(exists.getUserId())) {
            throw new ServiceException("收货地址不存在");
        }
        shopConsumerAddressMapper.deleteById(id);
    }

    /** DTO → 实体：只认 DTO 上那几格，nickname/username 那些回显字段传了也进不来 */
    private ShopConsumerAddress toAddress(ShopAddressSaveDto form) {
        ShopConsumerAddress address = new ShopConsumerAddress();
        BeanUtils.copyProperties(form, address);
        return address;
    }

    /**
     * receiver_name / receiver_phone / detail 在表上是 NOT NULL，缺了要到 INSERT 才报错，这里提前拦。
     * area_zh 不在这里生成：它跟 area_code 一样是前端地图选完/级联选完一起送来的（口径同门店、用户管理）。
     */
    private void assertRequired(ShopConsumerAddress address) {
        if (StringUtils.isBlank(address.getReceiverName())) {
            throw new ServiceException("收货人姓名不能为空");
        }
        if (StringUtils.isBlank(address.getReceiverPhone())) {
            throw new ServiceException("收货人电话不能为空");
        }
        if (StringUtils.isBlank(address.getDetail())) {
            throw new ServiceException("详细地址不能为空");
        }
    }

    /** 新增那一路才要确认：地址是给这个人存的，这个人已经不在库里就别落这一行 */
    private void assertUserExists(Long loginUserId) {
        if (sysUserMapper.selectById(loginUserId) == null) {
            throw new ServiceException("当前登录账号已经不存在了，请重新登录后再试");
        }
    }

    /**
     * 同一 user_id 下只能有一条 is_default=1，所以设默认前先把其余的置 0。
     * 走 LambdaUpdateWrapper 而不是逐条改：一个账号的地址可能有很多条。
     * 全局逻辑删除会给这条 UPDATE 自动补上 is_deleted = 0，不会碰到已删的行。
     *
     * @param excludeId 修改那一路传自己的 id（别把自己那条也清了），新增那一路传 null
     */
    private void clearOtherDefault(Long userId, Integer isDefault, Long excludeId) {
        if (!Integer.valueOf(DEFAULT_YES).equals(isDefault)) {
            return;
        }
        shopConsumerAddressMapper.update(null, new LambdaUpdateWrapper<ShopConsumerAddress>()
                .set(ShopConsumerAddress::getIsDefault, DEFAULT_NO)
                .eq(ShopConsumerAddress::getUserId, userId)
                .ne(excludeId != null, ShopConsumerAddress::getId, excludeId));
    }
}
