package com.shop.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.api.service.ShopTrainingSignService;
import com.shop.dto.training.ShopTrainingSignDto;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.ShopStore;
import com.shop.entity.ShopStoreTraining;
import com.shop.entity.ShopStoreTrainingMember;
import com.shop.entity.SysUser;
import com.shop.exception.ServiceException;
import com.shop.mapper.ShopStoreMapper;
import com.shop.mapper.ShopStoreTrainingMapper;
import com.shop.mapper.ShopStoreTrainingMemberMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.DateUtil;
import com.shop.utils.LoginUserUtils;
import com.shop.vo.training.ShopTrainingAttendVo;
import com.shop.vo.training.ShopTrainingSignVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 培训报名 / 签到 服务实现（签到是 2026-09-24 这一轮并进来的，两颗共用同一套"我是谁、我那一行报名记录"的判据）
 *
 * 鉴权同下单、接单那几路：/api/** 不过 Sa-Token 拦截器，所以这里自己取登录态，
 * 取不到就报"请先登录后再报名培训"（签到那一路文案同理，各说各的）。
 *
 * **谁能报名**判的不是身份档（account_type），而是"这个账号挂在哪家门店上"：
 * 报名表那一行 store_id 是 NOT NULL 的，也就是说这一张表天生只记门店的人，
 * 判"名下有没有门店"比再钉一档账号类型更直接，也少一处会漂移的口径 ——
 * 门店的绑定账号（shop_store.user_id）和店长（manager_id）都算，口径同 myStoreIds()，
 * 平台/ERP 那几档一般两家店都不沾，自然被挡在外面（要替别人登记是后台那一格的活，本期还没做）。
 * 再叠一层：**一场培训属于哪家门店（shop_store_training.store_id）是必填的**（2026-09-24 起「总部统一培训」
 * 那一档取消了），所以能报这一场的人就是那家店的绑定账号/店长，报名参数里根本没有门店那一格可填
 * （见 {@link #resolveStore}）。名下有店但不是这一场那家店的人，同样报不进。
 *
 * **报的是哪一场**只看 status：培训是"报名中"（1）才收，0 待发布 / 2 已截止 / 3 进行中 /
 * 4 已结束 / 5 已取消 都不收。另外两格时间（sign_start / sign_end）填了就一并判，
 * 没填就当那一头不设限 —— 状态和窗口是两回事，一个是人手动推的，一个是日历上的，两道都过得保险。
 *
 * **签到反过来不挡状态机**：只挡 5 已取消 那一场，不看是不是"3 进行中"、也不看 start_time 到没到。
 * 理由是这一页的状态和开始时间都是后台手动填的（忘了翻状态、时间填歪都是常事），
 * 而"人到场了"是当场的事实 —— 拿一格手动状态和日历把到了场的人挡在门外，比让他回头再补一次值钱。
 * 要收成"只有 3 进行中能签"，改 {@link #attend} 里那一句判据就够了。
 *
 * **已报名人数**这一列（shop_store_training.sign_count）这里不回写：读的时候一律按报名表现算
 * （列表和详情那条 SQL 里的 count 子查询），所以列上钉了 NEVER，写路径也不碰它，
 * 不会出现"改了状态把人数刷没"这种事。名额判据现数一次，返回给前端的也是现数的那一个。
 *
 * 并发：名额是"先数后插"，同一瞬间两个人挤最后一个名额会都成功（超一两个）。
 * 真要严格得给培训表加一配额列走 CAS，报名这种量级不值当，先记在这儿。
 * 重复点击不会写重：表上 uk_training_staff(training_id,user_id) 是唯一键，
 * 前置查一次给出人话提示，键真被撞了（并发的另一次刚插进去）也兜底成同一句话。
 * 签到没有这类问题：只改自己那一行，而且 attend_status 进 WHERE，重复点击第二次改不动。
 */
@Service
@RequiredArgsConstructor
public class ShopTrainingSignServiceImpl implements ShopTrainingSignService {

    private final ShopStoreTrainingMapper shopStoreTrainingMapper;

    private final ShopStoreTrainingMemberMapper shopStoreTrainingMemberMapper;

    /** 只用来反查"这个账号名下有哪几家门店" */
    private final ShopStoreMapper shopStoreMapper;

    /** 只用来取报名人的姓名/电话快照（登录态那份快照里没有手机号） */
    private final SysUserMapper sysUserMapper;

    /** 培训状态（字典 training_status）：1 报名中 —— 只有这一档收报名 */
    private static final int TRAINING_STATUS_SIGNING = 1;

    /** 报名状态（字典 training_sign_status）：0 未报名 —— 登记了但还没点报名的那一行 */
    private static final int SIGN_STATUS_NONE = 0;

    /** 报名状态：1 已报名 —— 占名额的就是这一档，取消的不占 */
    private static final int SIGN_STATUS_SIGNED = 1;

    /** 报名状态：2 已取消 —— 以前报过又退了，这一行还在（uk 只让一行），重新报名是把它复活 */
    private static final int SIGN_STATUS_CANCELLED = 2;

    /** 取消/未报名这两档：重新报名走 UPDATE 复活，不新插一行 */
    private static final List<Integer> SIGN_STATUS_RESIGNABLE =
            Arrays.asList(SIGN_STATUS_NONE, SIGN_STATUS_CANCELLED);

    /** 培训状态（字典 training_status）：5 已取消 —— 唯一不收签到的一档，见类注释 */
    private static final int TRAINING_STATUS_CANCELLED = 5;

    /** 出勤状态（字典 training_attend_status）：0 未签到 —— 报名那一步建出来就是这个值 */
    private static final int ATTEND_STATUS_NONE = 0;

    /** 出勤状态：1 已签到 */
    private static final int ATTEND_STATUS_ATTENDED = 1;

    @Override
    public ShopTrainingSignVo sign(ShopTrainingSignDto form) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再报名培训");
        if (form == null || form.getTrainingId() == null) {
            throw new ServiceException("请选择要报名的培训");
        }
        ShopStoreTraining training = shopStoreTrainingMapper.selectById(form.getTrainingId());
        if (training == null) {
            throw new ServiceException("培训不存在");
        }
        assertSignable(training);

        ShopStore store = resolveStore(login, training);
        SysUser signer = sysUserMapper.selectById(login.getId());
        if (signer == null) {
            throw new ServiceException("当前登录账号已经不存在了，请重新登录后再报名");
        }
        String userName = StringUtils.defaultIfBlank(signer.getNickname(), signer.getUsername());
        if (StringUtils.isBlank(userName)) {
            // user_name 那一列是 NOT NULL 的快照，昵号和用户名都空着（历史数据）就按 id 顶上，别整条报不上
            userName = "用户#" + login.getId();
        }

        Long userId = login.getId().longValue();
        ShopStoreTrainingMember exists = shopStoreTrainingMemberMapper.selectOne(
                new LambdaQueryWrapper<ShopStoreTrainingMember>()
                        .eq(ShopStoreTrainingMember::getTrainingId, training.getId())
                        .eq(ShopStoreTrainingMember::getUserId, userId)
                        .last("LIMIT 1"));
        if (exists != null && Integer.valueOf(SIGN_STATUS_SIGNED).equals(exists.getSignStatus())) {
            throw new ServiceException("你已经报过这场培训了，不用重复报名");
        }
        assertRoomLeft(training);

        LocalDateTime now = LocalDateTime.now();
        Long memberId = exists == null
                ? insertMember(training, store, userId, userName, signer.getMobile(), now)
                : reviveMember(exists, store, userName, signer.getMobile(), now);

        ShopTrainingSignVo vo = new ShopTrainingSignVo();
        vo.setMemberId(memberId);
        vo.setTrainingId(training.getId());
        vo.setTrainingNo(training.getTrainingNo());
        vo.setTrainingName(training.getTrainingName());
        vo.setStoreId(store.getId());
        vo.setStoreName(store.getStoreName());
        vo.setUserName(userName);
        vo.setSignStatus(SIGN_STATUS_SIGNED);
        vo.setSignTime(now);
        vo.setSignedCount(countSigned(training.getId()));
        vo.setMaxCount(training.getMaxCount());
        return vo;
    }

    @Override
    public ShopTrainingAttendVo attend(Long trainingId) {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("后再签到培训");
        if (trainingId == null) {
            throw new ServiceException("请选择要签到的培训");
        }
        ShopStoreTraining training = shopStoreTrainingMapper.selectById(trainingId);
        if (training == null) {
            throw new ServiceException("培训不存在");
        }
        // 只挡已取消那一场：状态和开始时间都是后台手动填的，人到了场就是事实（见类注释）
        if (Integer.valueOf(TRAINING_STATUS_CANCELLED).equals(training.getStatus())) {
            throw new ServiceException("这场培训已经取消，签到不了了");
        }

        // 签到不认参数里的 user_id：能签的只有自己那一行报名记录，找不到就是没报名
        ShopStoreTrainingMember member = shopStoreTrainingMemberMapper.selectOne(
                new LambdaQueryWrapper<ShopStoreTrainingMember>()
                        .eq(ShopStoreTrainingMember::getTrainingId, training.getId())
                        .eq(ShopStoreTrainingMember::getUserId, login.getId().longValue())
                        .last("LIMIT 1"));
        if (member == null || !Integer.valueOf(SIGN_STATUS_SIGNED).equals(member.getSignStatus())) {
            throw new ServiceException("你还没有报名这场培训，不能签到");
        }
        if (Integer.valueOf(ATTEND_STATUS_ATTENDED).equals(member.getAttendStatus())) {
            throw new ServiceException("你已经签过到了，不用重复签到");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = shopStoreTrainingMemberMapper.update(null,
                new LambdaUpdateWrapper<ShopStoreTrainingMember>()
                        .set(ShopStoreTrainingMember::getAttendStatus, ATTEND_STATUS_ATTENDED)
                        .set(ShopStoreTrainingMember::getAttendTime, now)
                        .set(ShopStoreTrainingMember::getUpdateTime, now)
                        .eq(ShopStoreTrainingMember::getId, member.getId())
                        // 出勤状态进 WHERE：前置查那一句挡重复点击（给人话提示），这一句才是真挡并发
                        .eq(ShopStoreTrainingMember::getAttendStatus, ATTEND_STATUS_NONE));
        if (updated == 0) {
            throw new ServiceException("这一场的签到状态刚刚被人改过，请刷新后重试");
        }

        ShopTrainingAttendVo vo = new ShopTrainingAttendVo();
        vo.setMemberId(member.getId());
        vo.setTrainingId(training.getId());
        vo.setTrainingNo(training.getTrainingNo());
        vo.setTrainingName(training.getTrainingName());
        vo.setAttendStatus(ATTEND_STATUS_ATTENDED);
        vo.setAttendTime(now);
        return vo;
    }

    @Override
    public List<ShopStore> signStores() {
        LoginUserInfo login = LoginUserUtils.requireLoginUser("登录后再查看可报名门店");
        return myStores(login);
    }

    /**
     * 这一场收不收报名：状态 + 报名窗口两道。
     * 状态单独把 0 待发布 说清楚，是因为这一档最常见（培训刚建出来还没发布），
     * 一句"状态不行"会让人以为是自己操作错了。
     */
    private void assertSignable(ShopStoreTraining training) {
        Integer status = training.getStatus();
        if (status == null || status != TRAINING_STATUS_SIGNING) {
            throw new ServiceException(status != null && status == 0
                    ? "这场培训还没发布，暂时不能报名"
                    : "只有「报名中」的培训能报名，这一场现在的状态不行");
        }
        LocalDateTime now = LocalDateTime.now();
        if (training.getSignStart() != null && now.isBefore(training.getSignStart())) {
            throw new ServiceException("报名还没开始，开放时间："
                    + DateUtil.formatLocalDateTime(training.getSignStart(), DateUtil.YYYY_MM_DD_HH_MM_SS));
        }
        if (training.getSignEnd() != null && now.isAfter(training.getSignEnd())) {
            throw new ServiceException("这场培训的报名已经截止了");
        }
    }

    /**
     * 名额：只数 1 已报名 的那几行（取消掉的给人腾出来了），max_count 为空就是不限。
     * 判的是"这会儿还有没有位置"，所以放在写之前一行；报错文案直接把当前人数带上，省得调用方自己再去数。
     */
    private void assertRoomLeft(ShopStoreTraining training) {
        Integer max = training.getMaxCount();
        if (max == null) {
            return;
        }
        int signed = countSigned(training.getId());
        if (signed >= max) {
            throw new ServiceException("这场培训名额已满（" + signed + "/" + max + " 人）");
        }
    }

    /** 现数这一场已报名的人数（口径和列表、详情那条 SQL 里的 count 子查询一致） */
    private int countSigned(Long trainingId) {
        Long count = shopStoreTrainingMemberMapper.selectCount(new LambdaQueryWrapper<ShopStoreTrainingMember>()
                .eq(ShopStoreTrainingMember::getTrainingId, trainingId)
                .eq(ShopStoreTrainingMember::getSignStatus, SIGN_STATUS_SIGNED));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 第一次报名：插一行。attend_status 不写，让库里那个默认值 0 未签到 顶上
     * （签到是培训当天的另一件事，不在这一步）。
     */
    private Long insertMember(ShopStoreTraining training, ShopStore store, Long userId,
                              String userName, String userPhone, LocalDateTime now) {
        ShopStoreTrainingMember member = new ShopStoreTrainingMember();
        member.setTrainingId(training.getId());
        member.setStoreId(store.getId());
        member.setUserId(userId);
        member.setUserName(userName);
        member.setUserPhone(userPhone);
        member.setSignStatus(SIGN_STATUS_SIGNED);
        member.setSignTime(now);
        try {
            shopStoreTrainingMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            // 两种情况撞这一句：并发的另一次报名刚插进去，或者上面那次前置查只看到未删除的行、
            // 而一条已删除的旧报名还占着 uk_training_staff（同购物车 uk_user_product 那个坑）
            throw new ServiceException("你已经报过这场培训了，不用重复报名");
        }
        return member.getId();
    }

    /**
     * 重新报名：把原先那一行翻回 1 已报名，顺带刷新门店和姓名/电话快照（换店、改名之后重新报，
     * 记的应该是这一次的实际情况），报名时间也重写成这一次。
     *
     * 状态进 WHERE：读到"没在报名中"和写之间要是被后台删了行、或者别的入口把它翻成了已报名，
     * 这一句就不成立 —— update(null, wrapper) 不走自动填充，所以 update_time 自己写。
     */
    private Long reviveMember(ShopStoreTrainingMember exists, ShopStore store,
                              String userName, String userPhone, LocalDateTime now) {
        int updated = shopStoreTrainingMemberMapper.update(null, new LambdaUpdateWrapper<ShopStoreTrainingMember>()
                .set(ShopStoreTrainingMember::getStoreId, store.getId())
                .set(ShopStoreTrainingMember::getUserName, userName)
                .set(ShopStoreTrainingMember::getUserPhone, userPhone)
                .set(ShopStoreTrainingMember::getSignStatus, SIGN_STATUS_SIGNED)
                .set(ShopStoreTrainingMember::getSignTime, now)
                .set(ShopStoreTrainingMember::getUpdateTime, now)
                .eq(ShopStoreTrainingMember::getId, exists.getId())
                .in(ShopStoreTrainingMember::getSignStatus, SIGN_STATUS_RESIGNABLE));
        if (updated == 0) {
            throw new ServiceException("这场培训的报名状态刚刚被人改过，请刷新后重试");
        }
        return exists.getId();
    }

    /**
     * 定位"以哪家门店报名"：就是这一场培训自己的所属门店，参数里没有门店那一格。
     *
     * 2026-09-24 起「总部统一培训」那一档取消了（他要的：所属门店必填），所以这里只有一条路 ——
     * 这一场是谁家的培训，就只有那家店的人能报，名额也是那家店的名额。
     * 库里那一列还允许 NULL（历史行没这个信息，不加 NOT NULL，口径同采购单的 store_id），
     * 真撞上一行 NULL 就直说让人先去后台补门店，不猜他是想报哪家。
     */
    private ShopStore resolveStore(LoginUserInfo login, ShopStoreTraining training) {
        if (training.getStoreId() == null) {
            throw new ServiceException("这一场培训还没有指定所属门店，先在后台给它补上门店再来报名");
        }
        List<ShopStore> mine = myStores(login);
        if (mine.isEmpty()) {
            throw new ServiceException("当前账号没有绑定任何门店，报不了培训（门店的绑定账号或店长都算）");
        }
        return mine.stream()
                .filter(item -> training.getStoreId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("这场培训是「" + storeLabel(training.getStoreId())
                        + "」的专场，只有这家店的绑定账号或店长能报名"));
    }

    /**
     * 报错文案里要说清是哪一家店。培训行上那一格门店名是列表 SQL 联表现取的（selectById 不带），
     * 所以只有走到"不是这家店的人来报"这一句才去查一次名字；查不到（门店已被删）就退回显示 id。
     */
    private String storeLabel(Long storeId) {
        ShopStore store = shopStoreMapper.selectById(storeId);
        return store == null ? "门店#" + storeId : store.getStoreName();
    }

    /**
     * 这个账号名下能用来报名的门店：绑定账号（shop_store.user_id）OR 店长（manager_id）命中的那些，
     * 口径同门店服务层的 myStoreIds()（两处不一致就会出现"列表看得到、报不了名"）。
     *
     * 这里**不判空**：空列表对报名那一路是错误（resolveStore 报人话），对「可报名门店」那一路只是
     * "这一格按钮和你无关"，所以两种处理都由调用方决定。
     * 只取 id 和门店名两列 —— 前端那一格下拉只要知道是哪家店，别把经纬度、合同信息整行搬过去。
     */
    private List<ShopStore> myStores(LoginUserInfo login) {
        Long userId = login.getId().longValue();
        // OR 必须包在嵌套 and 里，否则会和逻辑删除条件拼在同一层，把 is_deleted = 0 一起放行
        return shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>()
                .select(ShopStore::getId, ShopStore::getStoreName)
                .and(w -> w.eq(ShopStore::getUserId, userId)
                        .or()
                        .eq(ShopStore::getManagerId, userId))
                .orderByAsc(ShopStore::getId));
    }
}
