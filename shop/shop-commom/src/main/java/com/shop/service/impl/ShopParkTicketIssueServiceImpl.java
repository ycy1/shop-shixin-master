package com.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.dto.park.ShopParkTicketIssueDto;
import com.shop.entity.ShopParkUserTicket;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkTicket;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopParkUserTicketMapper;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkTicketMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ParkTicketQrStorage;
import com.shop.service.ShopParkTicketIssueService;
import com.shop.utils.GeneratorNoUtils;
import com.shop.utils.ParkTicketSlotUtils;
import com.shop.utils.QrCodeUtils;
import com.shop.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 出票这一条链的唯一落库处（口径见接口注释）
 *
 * 容错方向：票一定要出得下来，票面二维码是附属品。所以生成/上传那一步整个包在 try 里只记日志，
 * 不回滚、不报错（和集齐奖励那条"先落锚行、兑现失败只 warn 并写进说明"同一个取向）——
 * 图没了还能按票码手输核销，一张卖了钱的票不能因为文件存储抖一下就没了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopParkTicketIssueServiceImpl implements ShopParkTicketIssueService {

    /** 票种的上架档（只有上架的票种能出票） */
    private static final int TICKET_ON_SALE = 1;

    /** 出票那一刻的默认档（字典 user_ticket_status 的 0 未使用） */
    private static final int STATUS_UNUSED = 0;

    /** 园区的营业中档（停业的园区不该再卖票进来） */
    private static final int PARK_OPEN = 1;

    /** 一次最多出几张：代客出票一次点几十张是团队票，上百张就该走线下合同，别让一个输入框把库塞满 */
    private static final int MAX_QUANTITY_PER_ISSUE = 99;

    /** 票面二维码边长（像素），同 sys_user 那张二维码的口径 */
    private static final int QR_SIZE = 300;

    /**
     * 票码撞号最多重试几次：TK_+秒级时间戳+3 位随机数，一次出几张就是同一秒里连着插的，
     * 撞上的概率不是零（优惠券那侧因此改成按模板序号排），这里撞了就换一个码再来
     */
    private static final int MAX_CODE_RETRY = 5;

    private final ShopParkUserTicketMapper userTicketMapper;

    /** 票面那四格快照的源头 */
    private final ShopTrendyParkTicketMapper ticketMapper;

    /** 只用来看这张票去的园区还在不在、营业不营业 */
    private final ShopTrendyParkMapper parkMapper;

    /** 只用来认"票发给的这个人真存在"（sys_user.id 是 Integer，这里统一按 Long 传值） */
    private final SysUserMapper sysUserMapper;

    /**
     * 票面图片的存放口子，实现类在 shop-file（x-file-storage 的依赖在那一头，commom 够不着），
     * 所以这里按 ObjectProvider 拿：没有实现（比如只跑 shop-api 那侧的最小装配）就跳过出图，票照样出
     */
    private final ObjectProvider<ParkTicketQrStorage> qrStorageProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ShopParkUserTicket> issue(ShopParkTicketIssueDto dto) {
        // 一批票要么全落下要么全不落：出到一半报错会留下"付了钱只有几张票"的账，这里挡一下自调用绕过代理那条路
        TransactionUtils.requireActive("出票");
        if (dto == null) {
            throw new ServiceException("出票参数不能为空");
        }
        ShopTrendyParkTicket ticket = resolveTicket(dto);
        ShopTrendyPark park = parkMapper.selectById(ticket.getParkId());
        if (park == null) {
            throw new ServiceException("票种「" + ticket.getTicketName() + "」所属的园区不存在");
        }
        if (park.getStatus() == null || park.getStatus() != PARK_OPEN) {
            throw new ServiceException("园区「" + park.getParkName() + "」已停业，不能出票");
        }
        if (dto.getUserId() == null) {
            throw new ServiceException("请选择持票用户");
        }
        if (sysUserMapper.selectById(dto.getUserId()) == null) {
            throw new ServiceException("所选用户不存在");
        }
        if (dto.getVisitDate() == null) {
            throw new ServiceException("请选择游玩日期");
        }
        int quantity = resolveQuantity(dto, ticket);
        int timeSlot = resolveTimeSlot(dto, ticket);

        List<ShopParkUserTicket> issued = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            ShopParkUserTicket row = new ShopParkUserTicket();
            // 票面四格 + 园区一律从票种行上定格，请求体里传什么都不认（快照的源头只有票种那一行）
            row.setUserId(dto.getUserId());
            row.setTicketId(ticket.getId());
            row.setParkId(ticket.getParkId());
            row.setTicketName(ticket.getTicketName());
            row.setTicketType(ticket.getTicketType());
            row.setPrice(ticket.getPrice());
            row.setVisitDate(dto.getVisitDate());
            row.setTimeSlot(timeSlot);
            row.setHolderName(dto.getHolderName());
            row.setHolderPhone(dto.getHolderPhone());
            row.setHolderIdCard(dto.getHolderIdCard());
            row.setRemark(dto.getRemark());
            // 状态是流转出来的，不是填出来的：新出的一张票永远是未使用，那两个时间也一律留空
            row.setStatus(STATUS_UNUSED);
            insertWithFreshCode(row);
            issued.add(row);
            if (!Boolean.FALSE.equals(dto.getWithQrCode())) {
                attachQrCode(row);
            }
        }
        return issued;
    }

    private ShopTrendyParkTicket resolveTicket(ShopParkTicketIssueDto dto) {
        if (dto.getTicketId() == null) {
            throw new ServiceException("请选择票种");
        }
        ShopTrendyParkTicket ticket = ticketMapper.selectById(dto.getTicketId());
        if (ticket == null) {
            throw new ServiceException("所选票种不存在");
        }
        if (ticket.getStatus() == null || ticket.getStatus() != TICKET_ON_SALE) {
            throw new ServiceException("票种「" + ticket.getTicketName() + "」已下架，请先在「票种管理」里上架再出票");
        }
        return ticket;
    }

    /**
     * 几张：不传就是 1 张；支付那侧是按订单明细的件数传进来的，所以还要过一道票种自己的限购
     */
    private int resolveQuantity(ShopParkTicketIssueDto dto, ShopTrendyParkTicket ticket) {
        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity < 1) {
            throw new ServiceException("出票张数至少 1 张");
        }
        if (quantity > MAX_QUANTITY_PER_ISSUE) {
            throw new ServiceException("一次最多出 " + MAX_QUANTITY_PER_ISSUE + " 张，再多想走线下合同");
        }
        if (ticket.getMaxPerOrder() != null && quantity > ticket.getMaxPerOrder()) {
            throw new ServiceException("票种「" + ticket.getTicketName() + "」每单限购 " + ticket.getMaxPerOrder() + " 张");
        }
        return quantity;
    }

    /**
     * 这一张票约的时段：票种只有「全天」一档时钉 0（这时请求体里传别的档是错的），
     * 票种卖多档时必须从里面挑一个 —— 挑法由调用方决定（后台是人在挑，支付那侧是买家在挑）
     */
    private int resolveTimeSlot(ShopParkTicketIssueDto dto, ShopTrendyParkTicket ticket) {
        List<Integer> allowed = ParkTicketSlotUtils.parse(ticket.getTimeSlots());
        boolean onlyAllDay = allowed.size() == 1 && allowed.get(0) == ParkTicketSlotUtils.SLOT_ALL_DAY;
        if (onlyAllDay) {
            if (dto.getTimeSlot() != null && dto.getTimeSlot() != ParkTicketSlotUtils.SLOT_ALL_DAY) {
                throw new ServiceException("票种「" + ticket.getTicketName() + "」只有「全天」一个入园时段");
            }
            return ParkTicketSlotUtils.SLOT_ALL_DAY;
        }
        if (dto.getTimeSlot() == null) {
            throw new ServiceException("票种「" + ticket.getTicketName() + "」卖的是上午/下午/夜场，请挑一个入园时段");
        }
        if (!ParkTicketSlotUtils.contains(ticket.getTimeSlots(), dto.getTimeSlot())) {
            throw new ServiceException("票种「" + ticket.getTicketName() + "」不卖这个入园时段，可选的那几档在票种上看一眼");
        }
        return dto.getTimeSlot();
    }

    /**
     * 插一行，票码撞了就换一个再来（撞在这一步只会是同一批里连着出的那几张撞在一起，不是别人占的）
     */
    private void insertWithFreshCode(ShopParkUserTicket row) {
        for (int attempt = 1; ; attempt++) {
            row.setTicketCode(GeneratorNoUtils.getParkUserTicketCode());
            try {
                userTicketMapper.insert(row);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_CODE_RETRY) {
                    throw new ServiceException("出票失败：票码连着撞了 " + attempt + " 次，请过一秒再试");
                }
                row.setId(null);
            }
        }
    }

    /**
     * 票面二维码（出票那一路）：内容就是这一行的票码（闸机/后台扫到这一串就定位到这张票），
     * PNG 存进文件存储后把 URL 落回 qr_img。
     * 整段失败只记日志：这一张票已经卖出去了、行也已经落下了，不能因为一张图回滚掉 ——
     * 图没了还能按票码手输核销，事后也能在列表上点「重出二维码」补一张（那一条是要报错的）。
     */
    private void attachQrCode(ShopParkUserTicket row) {
        try {
            persistQrImg(row, buildAndUploadQr(row));
        } catch (Exception e) {
            log.warn("票面二维码生成失败，票 {} 已经出下来、qr_img 留空", row.getTicketCode(), e);
        }
    }

    @Override
    public String regenerateQrCode(Long id) {
        if (id == null) {
            throw new ServiceException("票ID不能为空");
        }
        ShopParkUserTicket row = userTicketMapper.selectById(id);
        if (row == null) {
            throw new ServiceException("这张票不存在");
        }
        if (StringUtils.isBlank(row.getTicketCode())) {
            // 没有票码就没有内容可编码（二维码装的是这一串，重出不是重新发一张票）
            throw new ServiceException("这一张票没有票码，出不了票面二维码");
        }
        String url = buildAndUploadQr(row);
        persistQrImg(row, url);
        return url;
    }

    /**
     * 出图 + 上传，返回可访问的 URL；每一步失败都直接抛（调用方决定是咽下去还是报给人看）
     */
    private String buildAndUploadQr(ShopParkUserTicket row) {
        ParkTicketQrStorage storage = qrStorageProvider.getIfAvailable();
        if (storage == null) {
            throw new ServiceException("没有票面二维码的存放实现（shop-file 那侧没装配起来）");
        }
        byte[] png;
        try {
            png = QrCodeUtils.generatePng(row.getTicketCode(), QR_SIZE);
        } catch (IOException e) {
            throw new ServiceException("票面二维码生成失败：" + e.getMessage());
        }
        String url = storage.uploadPng(png, row.getTicketCode() + ".png");
        if (StringUtils.isBlank(url)) {
            throw new ServiceException("票面二维码没拿到可访问的地址");
        }
        return url;
    }

    /**
     * 把 URL 写回 qr_img：不能走 updateById(只带 id + qrImg 的实体)——
     * 这一张实体上有好几格 IGNORED，那种写法会把持票人姓名/电话/备注一起写成 null，所以按列单独 SET 这一格
     */
    private void persistQrImg(ShopParkUserTicket row, String url) {
        userTicketMapper.update(null, new LambdaUpdateWrapper<ShopParkUserTicket>()
                .set(ShopParkUserTicket::getQrImg, url)
                .set(ShopParkUserTicket::getUpdateTime, LocalDateTime.now())
                .eq(ShopParkUserTicket::getId, row.getId()));
        row.setQrImg(url);
    }
}
