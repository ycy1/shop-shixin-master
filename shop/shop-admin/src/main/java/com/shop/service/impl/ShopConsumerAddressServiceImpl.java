package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.address.ShopAddressQueryDto;
import com.shop.dto.address.ShopAddressSaveDto;
import com.shop.entity.ShopConsumerAddress;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerAddressMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopConsumerAddressService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.address.ShopAddressVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消费者收货地址 服务实现（分页走自定义 XML 联表，写法照 shop_consumer_cart）
 *
 * 数据范围口径同购物车：消费者账号（account_type=3）只看/只动自己那几条，
 * 本表 user_id 是地址归属的消费者，不是后台创建人（创建人留痕在 create_by）。
 */
@Service
@RequiredArgsConstructor
public class ShopConsumerAddressServiceImpl extends ServiceImpl<ShopConsumerAddressMapper, ShopConsumerAddress>
        implements ShopConsumerAddressService {

    private static final int DEFAULT_YES = 1;

    private static final int DEFAULT_NO = 0;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopAddressVo> selectPage(ShopAddressQueryDto query) {
        if (query == null) {
            query = new ShopAddressQueryDto();
        }
        // 消费者账号只看自己的地址：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 关键字要同时搜收货人姓名和电话，还要带出消费者昵称，走 XML；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectAddressPage(PageUtil.getPage(), query);
    }

    @Override
    public ShopAddressVo selectAddressById(Long id) {
        ShopAddressVo address = baseMapper.selectAddressById(id);
        if (address == null) {
            throw new ServiceException("收货地址不存在");
        }
        assertOwn(address);
        return address;
    }

    @Override
    public void insert(ShopAddressSaveDto form) {
        ShopConsumerAddress address = toEntity(form);
        // 新增不接受调用方指定主键：id 是 AUTO，但传进来 MP 会照写
        address.setId(null);
        // 消费者账号只能往自己的地址簿里加，userId 同样覆盖
        if (LoginUserUtils.isConsumer()) {
            address.setUserId(StpUtil.getLoginIdAsLong());
        }
        validate(address);
        // 一个消费者的第一条地址直接当默认，不然他得再进一次编辑才能设上
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopConsumerAddress>()
                .eq(ShopConsumerAddress::getUserId, address.getUserId()));
        if (count == null || count == 0) {
            address.setIsDefault(DEFAULT_YES);
        } else if (address.getIsDefault() == null) {
            address.setIsDefault(DEFAULT_NO);
        }
        // 先清掉该消费者其余的默认，再写这条：新记录还没入库，不用排除自己的 id
        clearOtherDefault(address);
        save(address);
    }

    @Override
    public void update(ShopAddressSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("收货地址ID不能为空");
        }
        ShopConsumerAddress exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("收货地址不存在");
        }
        assertOwn(exists);
        ShopConsumerAddress address = toEntity(form);
        // 局部更新时这几个 NOT NULL 列不传就沿用原值，否则会被 validate 拦下或被写成 0
        if (address.getUserId() == null) {
            address.setUserId(exists.getUserId());
        }
        // 消费者也不能把自己这条挪到别人名下
        assertOwn(address);
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
        validate(address);
        // 这次排除自己：把别的行清成非默认，自己这条紧接着写成默认
        clearOtherDefault(address);
        updateById(address);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 消费者账号删的必须是自己那几条：先按 id 捞出实体逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isConsumer()) {
            List<ShopConsumerAddress> addresses = listByIds(ids);
            for (ShopConsumerAddress address : addresses) {
                assertOwn(address);
            }
        }
        // removeByIds 走全局逻辑删除，只把 is_deleted 置 1。
        // 删掉默认那条不自动补选：下单时没有默认地址就让用户自己挑，比在服务层猜一个更安全。
        // 补一条新地址时 insert 会因为它已不存在（逻辑删除的行不计入 selectCount）而重新置为默认
        removeByIds(ids);
    }

    private ShopConsumerAddress toEntity(ShopAddressSaveDto form) {
        ShopConsumerAddress address = new ShopConsumerAddress();
        BeanUtils.copyProperties(form, address);
        return address;
    }

    /**
     * 同一 user_id 下只能有一条 is_default=1，所以设默认前先把其余的置 0。
     * 走 LambdaUpdateWrapper 而不是逐条 updateById：一个消费者的地址可能有很多条。
     * 全局逻辑删除会给这条 UPDATE 自动补上 is_deleted = 0，不会碰到已删的行。
     */
    private void clearOtherDefault(ShopConsumerAddress address) {
        if (!Integer.valueOf(DEFAULT_YES).equals(address.getIsDefault())) {
            return;
        }
        baseMapper.update(null, new LambdaUpdateWrapper<ShopConsumerAddress>()
                .set(ShopConsumerAddress::getIsDefault, DEFAULT_NO)
                .eq(ShopConsumerAddress::getUserId, address.getUserId())
                .ne(address.getId() != null, ShopConsumerAddress::getId, address.getId()));
    }

    /**
     * receiver_name / receiver_phone / detail 在表上是 NOT NULL，缺了要到 INSERT 才报错，这里提前拦。
     * area_zh 不在这里生成：它跟 area_code 是前端级联选完一起送来的（同门店、用户管理）。
     */
    private void validate(ShopConsumerAddress address) {
        if (address.getUserId() == null) {
            throw new ServiceException("请选择消费者");
        }
        if (sysUserMapper.selectById(address.getUserId()) == null) {
            throw new ServiceException("所选消费者不存在");
        }
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

    /**
     * 消费者账号的归属校验，口径同 selectPage 的数据范围：非消费者直接放过。
     * 列表已经收窄了，这里补的是"绕过列表直接拿 id/别人的 userId 打接口"那条路。
     */
    private void assertOwn(ShopConsumerAddress address) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        Long userId = address.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这条收货地址不属于你，无权查看或修改");
        }
    }
}
