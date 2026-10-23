package com.shop.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.dto.browse.ShopBrowseQueryDto;
import com.shop.dto.browse.ShopBrowseSaveDto;
import com.shop.entity.ShopConsumerBrowse;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopConsumerBrowseMapper;
import com.shop.mapper.ShopProductMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopConsumerBrowseService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.PageUtil;
import com.shop.vo.browse.ShopBrowseVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 浏览足迹 服务实现（分页走自定义 XML 联表，写法照收藏 ShopConsumerFavoriteServiceImpl）
 *
 * 数据范围同购物车、收藏、收货地址：只有消费者账号（account_type=3）按 user_id = 登录 id 收窄，
 * 平台/门店/送货员/供应商看全部。本表的 user_id 是"这条足迹属于哪个消费者"，不是创建人
 * （创建人留痕在 BaseEntity 的 create_by），所以别照抄 shop_* 的 storeIds 那套。
 *
 * 和那两张表不一样的是新增的语义：足迹是一人一商品一条、时间以最近为准，所以 insert 是 upsert
 * （先刷时间、刷不到才插行）；收藏那一路长得像，但未复活的重复会先报"已收藏过"（见 ShopConsumerFavoriteServiceImpl）。
 */
@Service
@RequiredArgsConstructor
public class ShopConsumerBrowseServiceImpl extends ServiceImpl<ShopConsumerBrowseMapper, ShopConsumerBrowse>
        implements ShopConsumerBrowseService {

    private final ShopProductMapper shopProductMapper;

    private final SysUserMapper sysUserMapper;

    @Override
    public IPage<ShopBrowseVo> selectPage(ShopBrowseQueryDto query) {
        if (query == null) {
            query = new ShopBrowseQueryDto();
        }
        // 消费者账号只看自己的足迹：userId 一律覆盖成登录 id，前端传什么都不认
        if (LoginUserUtils.isConsumer()) {
            query.setUserId(StpUtil.getLoginIdAsLong());
        }
        // 商品名/主图/单价/分类名都不在本表上，关键字要按商品名和编码搜，用 wrapper 表达不了联表，走 XML；
        // 代价是自定义 SQL 不走全局逻辑删除，is_deleted = 0 在 XML 里手写
        return baseMapper.selectBrowsePage(PageUtil.getPage(), query);
    }

    @Override
    public ShopBrowseVo selectBrowseById(Long id) {
        ShopBrowseVo browse = baseMapper.selectBrowseById(id);
        if (browse == null) {
            throw new ServiceException("浏览足迹不存在");
        }
        assertOwn(browse);
        return browse;
    }

    @Override
    public void insert(ShopBrowseSaveDto form) {
        ShopConsumerBrowse browse = toEntity(form);
        // 新增不接受调用方指定主键：id 是 AUTO，但传进来 MP 会照写
        browse.setId(null);
        // 消费者账号只能往自己的足迹里记，userId 同样覆盖，不让传别人 id 进来
        if (LoginUserUtils.isConsumer()) {
            browse.setUserId(StpUtil.getLoginIdAsLong());
        }
        validate(browse);
        if (browse.getBrowseTime() == null) {
            browse.setBrowseTime(LocalDateTime.now());
        }
        // 已经有这一对 (消费者, 商品) 了（包括被逻辑删除的）就只把浏览时间刷成最新，不再插第二条：
        // 表上 uk_user_product 一人一商品只允许一条，删过的行也还占着这个键，刷新的同时把它复活
        int refreshed = baseMapper.markBrowsed(browse.getUserId(), browse.getProductId(), browse.getBrowseTime(),
                StpUtil.getLoginIdAsLong());
        if (refreshed > 0) {
            return;
        }
        try {
            save(browse);
        } catch (DuplicateKeyException e) {
            // 只可能是两个请求同时给同一 (消费者, 商品) 记第一次浏览，后到的那个撞在刚插好的行上；
            // 补一次刷新，最终结果和"记了一次浏览"完全一致，不必回头报错
            baseMapper.markBrowsed(browse.getUserId(), browse.getProductId(), browse.getBrowseTime(),
                    StpUtil.getLoginIdAsLong());
        }
    }

    @Override
    public void update(ShopBrowseSaveDto form) {
        if (form.getId() == null) {
            throw new ServiceException("浏览足迹ID不能为空");
        }
        ShopConsumerBrowse exists = getById(form.getId());
        if (exists == null) {
            throw new ServiceException("浏览足迹不存在");
        }
        assertOwn(exists);
        ShopConsumerBrowse browse = toEntity(form);
        // 归属消费者/商品/浏览时间允许不传（局部更新），不传沿用原值：这三列都是 NOT NULL
        if (browse.getUserId() == null) {
            browse.setUserId(exists.getUserId());
        }
        // 消费者也不能把自己这条挪到别人名下，挪完就等于把足迹送出去了
        assertOwn(browse);
        if (browse.getProductId() == null) {
            browse.setProductId(exists.getProductId());
        }
        if (browse.getBrowseTime() == null) {
            browse.setBrowseTime(exists.getBrowseTime());
        }
        validate(browse);
        // 改完的 (消费者, 商品) 如果库里已经有另一条，那一条才是该改的：一人一商品只留一条是这张表的约定
        Long count = baseMapper.countSameTarget(browse.getUserId(), browse.getProductId(), browse.getId());
        if (count != null && count > 0) {
            throw new ServiceException(duplicateMessage());
        }
        try {
            updateById(browse);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(duplicateMessage());
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 消费者账号删的必须是自己那几条：先按 id 捞出实体逐条校验，堵住直接拿别人 id 打接口这条路
        if (LoginUserUtils.isConsumer()) {
            List<ShopConsumerBrowse> browses = listByIds(ids);
            for (ShopConsumerBrowse browse : browses) {
                assertOwn(browse);
            }
        }
        // removeByIds 走全局逻辑删除，只把 is_deleted 置 1。
        // 这一列不在唯一键里，所以删掉不影响"再记一次浏览"——那条路是 upsert，会把这行连 is_deleted 一起改回来
        removeByIds(ids);
    }

    /**
     * 消费者账号的归属校验，口径同 selectPage 的数据范围：非消费者直接放过（平台/门店/供应商看全部）。
     * 列表已经收窄了，这里补的是"绕过列表直接拿 id/别人的 userId 打接口"那条路。
     */
    private void assertOwn(ShopConsumerBrowse browse) {
        if (!LoginUserUtils.isConsumer()) {
            return;
        }
        Long userId = browse.getUserId();
        if (userId == null || userId.longValue() != StpUtil.getLoginIdAsLong()) {
            throw new ServiceException("这条浏览足迹不属于你，无权查看或修改");
        }
    }

    /**
     * 商品名称、单价、昵称是回显字段，不在实体上，先按同名属性拷出一份只含 shop_consumer_browse 列的实体
     */
    private ShopConsumerBrowse toEntity(ShopBrowseSaveDto form) {
        ShopConsumerBrowse browse = new ShopConsumerBrowse();
        BeanUtils.copyProperties(form, browse);
        return browse;
    }

    /**
     * 校验必填，以及消费者、商品在库上真实存在（表上没有外键，所以只能在这里拦）
     */
    private void validate(ShopConsumerBrowse browse) {
        if (browse.getUserId() == null) {
            throw new ServiceException("请选择消费者");
        }
        if (sysUserMapper.selectById(browse.getUserId()) == null) {
            throw new ServiceException("所选消费者不存在");
        }
        if (browse.getProductId() == null) {
            throw new ServiceException("请选择商品");
        }
        if (shopProductMapper.selectById(browse.getProductId()) == null) {
            throw new ServiceException("所选商品不存在");
        }
    }

    private String duplicateMessage() {
        // 走到这里要么是前置查重命中，要么是并发插到了同一对 (消费者, 商品) 上；
        // 已删除的行也算在内，因为它还占着 uk_user_product，所以提示里不承诺"没浏览过"
        return "该消费者已有这件商品的浏览足迹（一个消费者一件商品只保留一条，浏览时间以最近为准），请直接修改那条记录";
    }
}
