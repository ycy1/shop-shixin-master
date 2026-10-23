package com.shop.api.service;

import com.shop.dto.training.ShopTrainingSignDto;
import com.shop.entity.ShopStore;
import com.shop.vo.training.ShopTrainingAttendVo;
import com.shop.vo.training.ShopTrainingSignVo;

import java.util.List;

/**
 * 培训报名 / 签到 服务接口（门店那一端和后台以后要共用的同一个入口）
 *
 * 为什么写在 shop-api：报名和签到都是"两端同一个动作"——门店店长在自己那一端点「报名」「签到」，
 * 和以后后台在「门店培训」的参训人员里替他登记，落的必须是同一个状态，
 * 所以入口和统一下单、统一接单一样放在 /api/comm（ShopStoreCommonController，
 * POST /api/comm/store/training/sign 和 /training/attend/{trainingId}）。
 * shop-api 只依赖 shop-commom，摸得到培训这两张表的 mapper，也摸不到后台那份 ShopStoreTrainingService。
 *
 * 两颗共用同一套"我是谁、我这一行报名记录"的判据，所以并在这一个接口里（同配送单那份
 * ShopDeliveryAcceptService 收下取货/送达/取消的写法）。
 */
public interface ShopTrainingSignService {

    /**
     * 给一场培训报名：在 shop_store_training_member 里建一行 1 已报名，记 sign_time。
     *
     * 三道闸（口径都在实现类的类注释里）：培训得是 1 报名中、当前时间在报名窗口内、
     * 名额没满（max_count 为空即不限）。另外这一场是**哪家门店的培训就得是那家店的人**
     * （所属门店必填，2026-09-24 起没有总部统一培训那一档了）—— 别家门店的账号来报会直接被拒。
     *
     * 幂等：一个人对同一场培训只可能有一行（表上 uk_training_staff），
     * 已经报过的再点一次不会被顶成"第二次报名"，而是直接告诉他"你已经报过了"；
     * 之前取消过（2 已取消 / 0 未报名）的那一行则原地复活成 1，并刷新姓名/电话快照和报名时间。
     *
     * @param form 报名参数（只有 trainingId 必填 —— 以哪家门店报名跟着这一场培训走，不由参数决定）
     * @return 报名结果，含写完之后现数出来的已报名人数
     */
    ShopTrainingSignVo sign(ShopTrainingSignDto form);

    /**
     * 列"这个账号名下有哪几家门店"，口径和 {@link #sign} 里那一道完全一样
     * （shop_store.user_id 绑定账号 OR manager_id 店长命中的那些）。
     *
     * 给前端做显示判据用的：「门店培训」列表操作列的「报名」那颗按钮只有在这一份里找得着
     * 那一场培训的所属门店时才出现 —— 空列表 / 找不着都是点了必被 {@link #sign} 拒，提前收掉按钮少一次报错。
     * 门店不再由前端挑（每家培训各归各家，参数里也没有门店那一格），这一份纯粹是"我能报哪几家开的场"。
     *
     * 和 {@link #sign} 的区别是这一颗**不抛异常**：不在报名范围就是空列表，
     * 那不是一个错误，是"这一页和你无关"。
     *
     * @return 名下可用于报名的门店（按 id 升序）；一家都没有时为空列表
     */
    List<ShopStore> signStores();

    /**
     * 给一场培训签到：把自己那一行报名记录的 attend_status 翻成 1 已签到，记 attend_time。
     *
     * 谁能签：只认登录态 —— 按 (trainingId, 登录 id) 找到那一行报名记录，入参里没有 user_id 这一格，
     * 传了也不认（否则就等于谁都能替别人签到）。门店也不用点名，那一行报名记录上已经写着是哪家店。
     *
     * 两道闸：这一行得是 1 已报名（没报名签什么到），培训不能是 5 已取消。
     * 幂等：已经签过的再点一次直接告诉他"你已经签过到了"，不会把签到时间刷成这一次
     * （状态进 WHERE，并发的第二次点击改不动那一行）。
     *
     * @param trainingId 培训id(shop_store_training.id)，必填
     * @return 签到结果（签到时间 + 现在哪一档出勤状态）
     */
    ShopTrainingAttendVo attend(Long trainingId);
}
