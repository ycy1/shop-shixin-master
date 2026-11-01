package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.entity.ShopParkUserTicket;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopParkUserTicketMapper;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkTicketMapper;
import com.shop.service.ShopTrendyParkTicketService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.ParkTicketSlotUtils;
import com.shop.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyParkTicketServiceImpl extends ServiceImpl<ShopTrendyParkTicketMapper, ShopTrendyParkTicket>
        implements ShopTrendyParkTicketService {

    /** 票种类型的五档（字典 ticket_type），超出这个范围的值页面渲染不出标签，落库前就拦掉 */
    private static final int TICKET_TYPE_MIN = 1;
    private static final int TICKET_TYPE_MAX = 5;

    // 入园时段那一格（多选，"1,2" 这种）的取值、去重、"全天不与他档并列"全在 ParkTicketSlotUtils 里判，
    // 出票那一路筛候选读的是同一份，两处不会走岔

    /** 只用来看所属园区真存在、并回填列表那一格园区名 */
    private final ShopTrendyParkMapper parkMapper;

    /** 只用来数"这个票种出出去几张"和删除前那道拦截 */
    private final ShopParkUserTicketMapper userTicketMapper;

    @Override
    public IPage<ShopTrendyParkTicket> selectPage(ShopTrendyParkTicket query) {
        if (query == null) {
            query = new ShopTrendyParkTicket();
        }
        IPage<ShopTrendyParkTicket> page = baseMapper.selectTicketPage(PageUtil.getPage(), query);
        fillParkName(page.getRecords());
        fillSoldCount(page.getRecords());
        return page;
    }

    @Override
    public List<ShopTrendyParkTicket> listOptions(Long parkId) {
        // 只给上架中的票种（下架的不该在出票表单里被新选中，口径同上面园区的 listOptions）；
        // 选了园区就把候选收窄到那一家，园区那一格留空就是全平台在售的票种
        List<ShopTrendyParkTicket> tickets = list(new LambdaQueryWrapper<ShopTrendyParkTicket>()
                .eq(ShopTrendyParkTicket::getStatus, 1)
                .eq(parkId != null, ShopTrendyParkTicket::getParkId, parkId)
                .orderByAsc(ShopTrendyParkTicket::getSort)
                .orderByAsc(ShopTrendyParkTicket::getId));
        // 这份候选要出现在「园区 · 票名 · 价格」那一串下拉文字里，所以园区名一并带回来（不联表，一趟批查）
        fillParkName(tickets);
        return tickets;
    }

    @Override
    public ShopTrendyParkTicket detail(Long id) {
        ShopTrendyParkTicket ticket = getById(id);
        if (ticket != null) {
            List<ShopTrendyParkTicket> one = new ArrayList<>(1);
            one.add(ticket);
            fillParkName(one);
        }
        return ticket;
    }

    @Override
    public void insert(ShopTrendyParkTicket ticket) {
        if (StringUtils.isBlank(ticket.getTicketNo())) {
            ticket.setTicketNo(GeneratorNoUtils.getParkTicketNo());
        }
        validate(ticket, null);
        normalize(ticket);
        try {
            save(ticket);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("票种编号「" + ticket.getTicketNo() + "」已被占用，可能来自一个已删除的票种");
        }
    }

    @Override
    public void update(ShopTrendyParkTicket ticket) {
        if (ticket.getId() == null) {
            throw new ServiceException("票种ID不能为空");
        }
        if (getById(ticket.getId()) == null) {
            throw new ServiceException("票种不存在");
        }
        validate(ticket, ticket.getId());
        normalize(ticket);
        try {
            updateById(ticket);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("票种编号「" + ticket.getTicketNo() + "」已被占用，可能来自一个已删除的票种");
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 已经发出去的票上存着这个票种的快照，删掉票种行之后那些票就指向一个不存在的票种
        //（列表读的是快照所以还显示得出名字，但"这张票是哪个票种卖的"这一路断了）—— 先拦一句
        Long sold = userTicketMapper.selectCount(new LambdaQueryWrapper<ShopParkUserTicket>()
                .in(ShopParkUserTicket::getTicketId, ids));
        if (sold != null && sold > 0) {
            throw new ServiceException("这个票种已经出出去 " + sold + " 张票，删掉之后那些票就没有票种可对，请先在「用户票种」里处理");
        }
        removeByIds(ids);
    }

    /**
     * 回填「所属园区」那一格的名字：按当页出现过的 park_id 一次查回来，不联表
     *（形状同 ShopStoreServiceImpl#fillBrand；园区后来被删掉就查不到，这一格留空、前端显示 '-'）
     */
    private void fillParkName(List<ShopTrendyParkTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        Set<Long> parkIds = new HashSet<>();
        for (ShopTrendyParkTicket ticket : tickets) {
            if (ticket.getParkId() != null) {
                parkIds.add(ticket.getParkId());
            }
        }
        if (parkIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (ShopTrendyPark park : parkMapper.selectList(new LambdaQueryWrapper<ShopTrendyPark>()
                .select(ShopTrendyPark::getId, ShopTrendyPark::getParkName)
                .in(ShopTrendyPark::getId, parkIds))) {
            nameById.put(park.getId(), park.getParkName());
        }
        for (ShopTrendyParkTicket ticket : tickets) {
            ticket.setParkName(nameById.get(ticket.getParkId()));
        }
    }

    /**
     * 回填「已出票数」：按当页这批票种 id 一次分组数完，别一行发一趟查询。
     * selectMaps 走 BaseMapper，逻辑删除条件自动带上 ⇒ 已删除的票不计数（口径同删除拦截那一趟）。
     */
    private void fillSoldCount(List<ShopTrendyParkTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(tickets.size());
        tickets.forEach(row -> ids.add(row.getId()));
        Map<Long, Integer> counted = new HashMap<>();
        for (Map<String, Object> one : userTicketMapper.selectMaps(new QueryWrapper<ShopParkUserTicket>()
                .select("ticket_id AS ticketId", "COUNT(1) AS cnt")
                .in("ticket_id", ids)
                .groupBy("ticket_id"))) {
            Object ticketId = one.get("ticketId");
            Object cnt = one.get("cnt");
            if (ticketId != null && cnt != null) {
                counted.put(((Number) ticketId).longValue(), ((Number) cnt).intValue());
            }
        }
        tickets.forEach(row -> row.setSoldCount(counted.getOrDefault(row.getId(), 0)));
    }

    /**
     * NOT NULL 那几格补默认值（和建表默认一致）：页面没给就等于走库的默认档
     */
    private void normalize(ShopTrendyParkTicket ticket) {
        if (ticket.getTicketType() == null) {
            ticket.setTicketType(TICKET_TYPE_MIN);
        }
        // 时段没给就是"全天"；给了就归一成库里该存的写法（去重、升序、"0" 不与他档并列）
        ticket.setTimeSlots(ParkTicketSlotUtils.normalize(ticket.getTimeSlots()));
        if (ticket.getStatus() == null) {
            ticket.setStatus(1);
        }
        if (ticket.getSort() == null) {
            ticket.setSort(0);
        }
    }

    /**
     * 名称/园区/票价必填 + 两档枚举取值 + 编号判重（excludeId 是修改时排除自己）
     */
    private void validate(ShopTrendyParkTicket ticket, Long excludeId) {
        if (StringUtils.isBlank(ticket.getTicketName())) {
            throw new ServiceException("票种名称不能为空");
        }
        if (ticket.getParkId() == null) {
            throw new ServiceException("请选择所属园区");
        }
        if (parkMapper.selectById(ticket.getParkId()) == null) {
            throw new ServiceException("所选园区不存在");
        }
        if (ticket.getPrice() == null) {
            throw new ServiceException("售价不能为空");
        }
        if (ticket.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("售价不能是负数");
        }
        if (ticket.getTicketType() != null
                && (ticket.getTicketType() < TICKET_TYPE_MIN || ticket.getTicketType() > TICKET_TYPE_MAX)) {
            throw new ServiceException("票种类型不合法（1日票 2年卡 3套票 4快速通行 5限定款购买资格）");
        }
        // 入园时段是多选那一串（varchar 存 "1,2"），取值/去重/"全天不与他档并列"都交给 ParkTicketSlotUtils 判，
        // 它同时是出票那一路筛候选时读的同一份口径
        ParkTicketSlotUtils.parse(ticket.getTimeSlots());
        // 这两格留空是"不限"（实体上是 IGNORED，清得掉），但填了就得是正数：0 或负数的有效期/限购没有意义
        if (ticket.getValidDays() != null && ticket.getValidDays() <= 0) {
            throw new ServiceException("有效天数要大于 0，不限就留空");
        }
        if (ticket.getMaxPerOrder() != null && ticket.getMaxPerOrder() <= 0) {
            throw new ServiceException("每单限购数量要大于 0，不限就留空");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ShopTrendyParkTicket>()
                .eq(ShopTrendyParkTicket::getTicketNo, ticket.getTicketNo())
                .ne(excludeId != null, ShopTrendyParkTicket::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("票种编号「" + ticket.getTicketNo() + "」已存在");
        }
    }
}
