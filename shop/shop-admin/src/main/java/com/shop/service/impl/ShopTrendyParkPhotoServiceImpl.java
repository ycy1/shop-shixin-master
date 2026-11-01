package com.shop.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shop.common.RedisConstants;
import com.shop.entity.ShopTrendyPark;
import com.shop.entity.ShopTrendyParkPhoto;
import com.shop.entity.SysUser;
import com.shop.enums.NotificationTypeEnum;
import com.shop.event.NotificationEvent;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopTrendyParkMapper;
import com.shop.mapper.ShopTrendyParkPhotoMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.service.ShopTrendyParkPhotoService;
import com.shop.utils.LoginUserUtils;
import com.shop.utils.NotificationsUtil;
import com.shop.utils.PageUtil;
import com.shop.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopTrendyParkPhotoServiceImpl extends ServiceImpl<ShopTrendyParkPhotoMapper, ShopTrendyParkPhoto>
        implements ShopTrendyParkPhotoService {

    /**
     * 来源/审核状态/两列长度这些取值都从实体上引（一张表一套数）：用户上传那一路在
     * shop-commom 的 ShopParkPhotoSubmitServiceImpl，两边读的是同一批常量，改列宽不会只改到一处。
     */
    private static final int SOURCE_OFFICIAL = ShopTrendyParkPhoto.SOURCE_OFFICIAL;
    private static final int SOURCE_USER = ShopTrendyParkPhoto.SOURCE_USER;
    private static final int STATUS_PENDING = ShopTrendyParkPhoto.STATUS_PENDING;
    private static final int STATUS_PASSED = ShopTrendyParkPhoto.STATUS_PASSED;
    private static final int STATUS_REJECTED = ShopTrendyParkPhoto.STATUS_REJECTED;
    private static final int STATUS_HIDDEN = ShopTrendyParkPhoto.STATUS_HIDDEN;
    private static final int IMAGE_URL_MAX = ShopTrendyParkPhoto.IMAGE_URL_MAX;
    private static final int DESCRIPTION_MAX = ShopTrendyParkPhoto.DESCRIPTION_MAX;
    private static final int AUDIT_MSG_MAX = ShopTrendyParkPhoto.AUDIT_MSG_MAX;

    /** 审核状态那串的取值区间（0 待审核 …… 3 已隐藏），只在这一份里判范围用 */
    private static final int STATUS_MIN = 0;
    private static final int STATUS_MAX = 3;

    /** 只用来看园区真存在、并把所属 IP 从园区行上带过来 */
    private final ShopTrendyParkMapper parkMapper;

    /** 只用来回填「上传人」那一格昵称 */
    private final SysUserMapper sysUserMapper;

    /** 标签词表就存在它那里（RedisConstants.PARK_PHOTO_TAG_KEY 那一串 Set） */
    private final RedisUtil redisUtil;

    /** 审核结论那条消息走它（发事件、落库和展开收件人都在提交后的异步那一段，口径同广场审核） */
    private final NotificationsUtil notificationsUtil;

    @Override
    public List<String> listTagOptions() {
        Set<Object> members = redisUtil.sMembers(RedisConstants.PARK_PHOTO_TAG_KEY);
        if (members == null || members.isEmpty()) {
            // 整串是空的（从没建过、或被清过库）：先把库里已在用的那批词灌进去当起点，之后就以 Redis 那份为准
            members = seedFromRows();
        }
        List<String> words = new ArrayList<>();
        for (Object one : members) {
            String word = one == null ? null : String.valueOf(one).trim();
            if (StringUtils.isNotBlank(word) && !words.contains(word)) {
                words.add(word);
            }
        }
        // Set 给回来的顺序是丢了的，不排就是刷新一次换个样（口径同首页热搜那一排）
        Collections.sort(words);
        return words;
    }

    @Override
    public IPage<ShopTrendyParkPhoto> selectPage(ShopTrendyParkPhoto query) {
        if (query == null) {
            query = new ShopTrendyParkPhoto();
        }
        IPage<ShopTrendyParkPhoto> page = baseMapper.selectPhotoPage(PageUtil.getPage(), query);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public ShopTrendyParkPhoto detail(Long id) {
        ShopTrendyParkPhoto photo = getById(id);
        if (photo != null) {
            List<ShopTrendyParkPhoto> one = new ArrayList<>(1);
            one.add(photo);
            fillNames(one);
        }
        return photo;
    }

    @Override
    public void insert(ShopTrendyParkPhoto photo) {
        validate(photo);
        normalize(photo);
        save(photo);
        registerTags(photo.getTags());
    }

    @Override
    public void update(ShopTrendyParkPhoto photo) {
        if (photo.getId() == null) {
            throw new ServiceException("照片ID不能为空");
        }
        if (getById(photo.getId()) == null) {
            throw new ServiceException("照片不存在");
        }
        validate(photo);
        normalize(photo);
        updateById(photo);
        registerTags(photo.getTags());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, Integer status, String auditMsg) {
        if (id == null) {
            throw new ServiceException("照片ID不能为空");
        }
        if (status == null || status < STATUS_MIN || status > STATUS_MAX) {
            throw new ServiceException("审核状态不合法（0待审核 1通过 2拒绝 3已隐藏）");
        }
        ShopTrendyParkPhoto photo = getById(id);
        if (photo == null) {
            throw new ServiceException("照片不存在");
        }
        // 空串归一成 NULL：审核意见是可选项，「通过但没写理由」要比库里留一个空串干净
        String message = StringUtils.isBlank(auditMsg) ? null : auditMsg.trim();
        if (message != null && message.length() > AUDIT_MSG_MAX) {
            throw new ServiceException("审核意见最多 " + AUDIT_MSG_MAX + " 个字");
        }
        Long loginUserId = LoginUserUtils.requireLoginUser("后审核园区照片").getId();
        // 走那一句专用 UPDATE，不走整行提交（原因见 Mapper 上那段）：状态和意见两格一起写，别的一列都不碰
        if (baseMapper.auditPhoto(id, status, message, loginUserId) != 1) {
            throw new ServiceException("照片不存在");
        }
        notifyAuditResult(photo, status, message);
    }

    /**
     * 审核结论发给上传人。
     *
     * 三种人不用吵：官方上传的那几行 user_id 是 NULL（没有收件人）；把结论退回 0 待审核是运营自己改主意，
     * 作者那边什么都没发生，发一条「还没审核」只会让人以为图出了岔子。所以只有 1/2/3 三个结论、
     * 且这张图确实是用户上传的，才发。
     *
     * 走事件不走直接调用（口径同广场审核结果）：audit 挂的是事务，通知发在事务里 ⇒ 一旦回滚，
     * 照片状态没变、作者却已经收到一句「审核通过」。
     *
     * 别的那几档 link 都留空、由各端自己按 businessType 拼路由，这一档刻意把目标填死在消息上：
     * business_id 是**照片**的 id，而落地页是「那家园区的全部照片」那一页 —— 照片属于哪家园区只有这一行知道，
     * 各端拿 business_id 拼不出来（要么每条消息多问一次后端，要么各端自己存一份）。
     * 拼的是 web 潮玩专区的路径（园区照片页只有这一端有），App 那侧认 link 之前先把这一档排掉、只标已读
     *（见 shop-app 的 utils/notification）。园区被删掉了就不给 link，别把人带进一个报错页。
     *
     * 同一张反复审核就重复提醒一次：不做去重 —— 那是有人重新对着这一张做了个决定，
     * 宁可多发也别让作者以为没审过（口径同广场那一路）。
     */
    private void notifyAuditResult(ShopTrendyParkPhoto photo, Integer status, String auditMsg) {
        if (photo.getUserId() == null || STATUS_PENDING == status) {
            return;
        }
        ShopTrendyPark park = parkMapper.selectById(photo.getParkId());
        // 园区被删掉了就没有可去的那一页：话照说、不挂跳转（link 留空，前端点了只标已读）
        String where = park == null ? "园区" : park.getParkName();
        String link = park == null
                ? null : "/trendy/park/" + park.getId() + "/photo?highlight=" + photo.getId();
        // 三档结论同一份拼法：写了意见就附在后面，没写就补一句接下来该怎么办（别让作者只收到半句"未通过审核"）
        String note = StringUtils.isBlank(auditMsg) ? "" : "：" + auditMsg;
        String title;
        String content;
        if (STATUS_PASSED == status) {
            title = "上传的园区照片已通过审核";
            content = "你在「" + where + "」上传的照片已通过审核，现在上到那家园区的照片墙上了"
                    + (StringUtils.isBlank(auditMsg) ? "" : "。运营留言：" + auditMsg);
        } else if (STATUS_REJECTED == status) {
            title = "上传的园区照片未通过审核";
            content = "你在「" + where + "」上传的照片未通过审核"
                    + (StringUtils.isBlank(auditMsg) ? "，可以换一张再传" : "：" + auditMsg);
        } else if (STATUS_HIDDEN == status) {
            title = "上传的园区照片已被隐藏";
            content = "你在「" + where + "」上传的照片已被隐藏，前台照片墙上暂时不再展示" + note;
        } else {
            // 0 待审核在上面就返回了，走到这里的只有 1/2/3；真冒出别的值就当没什么结论好说，不硬编一句话
            return;
        }
        notificationsUtil.publish(NotificationEvent.builder()
                .type(NotificationTypeEnum.PARK_PHOTO)
                .businessId(photo.getId())
                .title(title)
                .message(content)
                .link(link)
                .userId(photo.getUserId())
                .build());
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 没有别的表引用照片（用户票、订单都不指向它），所以不拦删除；删掉的就是这一张图本身
        removeByIds(ids);
    }

    /**
     * 园区必填且真存在 + 图片必填 + 两档枚举取值 + 标签那串得是能进 json 列的数组 + 两列长度。
     * 顺手把所属 IP 从园区行上带过来 —— 那一格不认人填的，两处各填一份迟早对不上。
     */
    private void validate(ShopTrendyParkPhoto photo) {
        if (photo.getParkId() == null) {
            throw new ServiceException("请选择所属园区");
        }
        ShopTrendyPark park = parkMapper.selectById(photo.getParkId());
        if (park == null) {
            throw new ServiceException("所选园区不存在");
        }
        if (StringUtils.isBlank(photo.getImageUrl())) {
            throw new ServiceException("请上传照片");
        }
        if (photo.getImageUrl().length() > IMAGE_URL_MAX) {
            throw new ServiceException("图片地址过长（最多 " + IMAGE_URL_MAX + " 个字符）");
        }
        if (photo.getSourceType() != null && photo.getSourceType() != SOURCE_OFFICIAL && photo.getSourceType() != SOURCE_USER) {
            throw new ServiceException("来源类型不合法（1官方上传 2用户上传）");
        }
        if (photo.getStatus() != null && (photo.getStatus() < STATUS_MIN || photo.getStatus() > STATUS_MAX)) {
            throw new ServiceException("审核状态不合法（0待审核 1通过 2拒绝 3已隐藏）");
        }
        if (photo.getIsFeatured() != null
                && photo.getIsFeatured() != 0 && photo.getIsFeatured() != 1) {
            throw new ServiceException("是否精选只能是 0 或 1");
        }
        if (photo.getDescription() != null && photo.getDescription().length() > DESCRIPTION_MAX) {
            throw new ServiceException("照片描述最多 " + DESCRIPTION_MAX + " 个字");
        }
        // tags 是 MySQL json 列：写进去的必须是一段合法 JSON 数组，否则整条保存直接报错，
        // 所以在这里就拦下来给一句人话，而不是让数据库抛 JSON parse exception
        if (StringUtils.isNotBlank(photo.getTags()) && !isJsonArray(photo.getTags())) {
            throw new ServiceException("标签要写成 JSON 数组，例如 [\"打卡\",\"夜景\"]");
        }
        // 所属 IP 不认人填的：以园区那一格为准（换了园区就跟着换），两处各填一份迟早对不上
        photo.setIpId(park.getIpId());
    }

    /**
     * NOT NULL 那几格补默认值（和建表默认一致）：页面没给就等于走库的默认档。
     *
     * 状态那一格的默认按来源分：官方自己上传的图默认就是「通过」（后台加一张还要点一次通过是白动作），
     * 用户上传的那一路默认「待审核」（这一期还没有写入方，规矩先留在服务层，开那条路时不用现想）。
     */
    private void normalize(ShopTrendyParkPhoto photo) {
        if (photo.getSourceType() == null) {
            photo.setSourceType(SOURCE_OFFICIAL);
        }
        if (photo.getStatus() == null) {
            photo.setStatus(photo.getSourceType() == SOURCE_USER ? STATUS_PENDING : STATUS_PASSED);
        }
        if (photo.getIsFeatured() == null) {
            photo.setIsFeatured(0);
        }
        if (photo.getSort() == null) {
            photo.setSort(0);
        }
        // 空串不是合法 JSON，一律归一成 "[]"（口径同门店的 cover_images、原来园区那一格 images）
        if (StringUtils.isBlank(photo.getTags())) {
            photo.setTags("[]");
        }
    }

    /**
     * 把这一行刚存进去的标签并进 Redis 那串词表：新敲的词从这一次起就能在候选里看到，
     * 已经在串里的由 Redis 自己去了重。词表只跟着「用」长，不跟着「删照片」缩。
     */
    private void registerTags(String raw) {
        List<String> words = parseTags(raw);
        if (!words.isEmpty()) {
            redisUtil.sAdd(RedisConstants.PARK_PHOTO_TAG_KEY, words.toArray());
        }
    }

    /**
     * Redis 那串是空的才跑：把库里每一行的标签聚成一串灌进去当起点（第一次用不必人一条条手打）。
     * 只读 tags 那一列，且走 BaseMapper ⇒ 逻辑删除的那些行不算。
     */
    private Set<Object> seedFromRows() {
        Set<String> words = new HashSet<>();
        for (ShopTrendyParkPhoto row : list(new LambdaQueryWrapper<ShopTrendyParkPhoto>()
                .select(ShopTrendyParkPhoto::getTags))) {
            words.addAll(parseTags(row.getTags()));
        }
        if (!words.isEmpty()) {
            redisUtil.sAdd(RedisConstants.PARK_PHOTO_TAG_KEY, words.toArray());
        }
        Set<Object> back = new HashSet<>();
        back.addAll(words);
        return back;
    }

    /** 那一列拆成字符串数组：空串、不是数组、里面夹空项，一律当「这张没打标签」，不让一行脏数据打断整串 */
    private List<String> parseTags(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try {
            for (Object one : JSONUtil.parseArray(raw)) {
                String word = one == null ? null : String.valueOf(one).trim();
                if (StringUtils.isNotBlank(word) && !out.contains(word)) {
                    out.add(word);
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return out;
    }

    private boolean isJsonArray(String raw) {
        try {
            JSONUtil.parseArray(raw);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 回填「所属园区」「上传人」那两格名字：按当页出现过的 id 一次查回来，不联表
     *（形状同 ShopTrendyParkTicketServiceImpl#fillParkName；人后来被删了就查不到，这一格留空、前端显示 '-'）
     */
    private void fillNames(List<ShopTrendyParkPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        Set<Long> parkIds = new HashSet<>();
        Set<Integer> userIds = new HashSet<>();
        for (ShopTrendyParkPhoto photo : photos) {
            if (photo.getParkId() != null) {
                parkIds.add(photo.getParkId());
            }
            // 官方上传那几行 user_id 是空的，不数进批次里
            if (photo.getUserId() != null) {
                userIds.add(photo.getUserId().intValue());
            }
        }
        Map<Long, String> parkNameById = new HashMap<>();
        if (!parkIds.isEmpty()) {
            for (ShopTrendyPark park : parkMapper.selectList(new LambdaQueryWrapper<ShopTrendyPark>()
                    .select(ShopTrendyPark::getId, ShopTrendyPark::getParkName)
                    .in(ShopTrendyPark::getId, parkIds))) {
                parkNameById.put(park.getId(), park.getParkName());
            }
        }
        Map<Long, String> userNameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                userNameById.put(user.getId().longValue(),
                        StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
            }
        }
        for (ShopTrendyParkPhoto photo : photos) {
            photo.setParkName(parkNameById.get(photo.getParkId()));
            photo.setUserName(userNameById.get(photo.getUserId()));
        }
    }
}
