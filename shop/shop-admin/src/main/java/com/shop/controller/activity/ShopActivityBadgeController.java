package com.shop.controller.activity;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.api.service.ShopActivityBadgeGrantService;
import com.shop.common.Result;
import com.shop.dto.activity.ShopActivityBadgeGiveDto;
import com.shop.entity.ShopActivityBadge;
import com.shop.entity.ShopActivityBadgeUser;
import com.shop.service.ShopActivityBadgeService;
import com.shop.service.ShopActivityBadgeUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 徽章管理（营销活动 -> 徽章管理）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在 add/update/delete/give/recordDelete/recordStatus/recordShow 上；
 * activity:badge:detail 和 activity:badge:record 那两条只给前端收「详情」「获取记录」两颗按钮用。
 *
 * 这一页有两处和别的 CRUD 页不一样：
 * ① 明细（用户徽章）只开三条写口子，都在「获取记录」弹窗里 —— 那颗「删除获取记录」和「状态」「是否展示」那两格的开关。
 *    往那张表插一行才是"发一枚徽章"（还顺带兑现了徽章上那笔积分和那几张券），所以新增/修改都不开；
 *    删只是把"这一枚在他手上"这条记录抹掉，改状态只是翻"这一枚还算不算数"，改展示只是翻"这一枚要不要给人看"，
 *    三样都不动积分和券（要收分走积分页那颗「修改」，要作废券走优惠券页，那两条本来就记得出是谁动的）；
 * ②「下发」的实现不在本模块，在 shop-api 的 ShopActivityBadgeGrantService（支付回调和签到徽章那条
 *    定时任务都住在那一端，那一端摸不到后台的 service）。判定和兑现只有一份，页面上下发的那一枚和用户买满之后
 *    自动发的那一枚才会走同一个唯一键、同一套奖励；权限和 @OperationLogger 仍压在这一层。
 *
 * 自动那两条触发点（支付回调、badgeTask 那条定时任务）不需要这一页的任何操作：达标了就发、已经拿过就跳过，
 * 由 uk_badge_user 兜着，所以运营只负责定门槛（add/update）和补发（give）。
 */
@Api(tags = "徽章管理")
@RestController
@RequestMapping("/activity/badge")
@RequiredArgsConstructor
public class ShopActivityBadgeController {

    private final ShopActivityBadgeService shopActivityBadgeService;

    /** 「获取记录」那颗按钮的明细，读的是另一张表（用户徽章），所以是第二个 service（同优惠券那页的 /record/list） */
    private final ShopActivityBadgeUserService shopActivityBadgeUserService;

    /** 发放徽章那一份（三条写入路共用的那一份，本模块只调手动下发这一格） */
    private final ShopActivityBadgeGrantService shopActivityBadgeGrantService;

    @GetMapping("/list")
    @ApiOperation(value = "获取徽章列表（联表带出目标名称：类型是门店/活动时联门店/活动表取名，签到/消费两类没有目标是 NULL；"
            + "已获得人数按明细表现数出来，库里没这一列）")
    public Result<IPage<ShopActivityBadge>> list(ShopActivityBadge badge) {
        return Result.success(shopActivityBadgeService.selectPage(badge));
    }

    /**
     * 这一条走的是另一张表（shop_activity_badge_user），所以路径挂在 /activity/badge 下面但由
     * ShopActivityBadgeUserService 实现 —— 和优惠券那页的 /record/list 同一个形状：明细跟着主数据那一页给入口。
     * badgeId 留空就是看全部徽章的获取记录。
     */
    @GetMapping("/record/list")
    @ApiOperation(value = "获取一枚徽章的获取记录（列表页那颗「获取记录」弹窗，按徽章分页、最新一条在前；"
            + "这一张表上只开删除、改状态、改展示三条写口子，别的列都改不了；"
            + "名称那三格读的是发放那一刻的快照，所以徽章后来改名换图不影响这份明细）")
    public Result<IPage<ShopActivityBadgeUser>> recordList(ShopActivityBadgeUser query) {
        return Result.success(shopActivityBadgeUserService.selectRecordPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条徽章详情（修改弹窗和只读详情弹窗共用）")
    public Result<ShopActivityBadge> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopActivityBadgeService.selectBadgeById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "添加徽章：状态不传按 1 启用；target_id 按类型分叉（1 门店必填、2 活动必填、3 签到和 4 消费由服务端清成 NULL）；"
            + "reach_count 留 0 就是这一枚只走手动下发（自动链路一枚都不发）")
    @OperationLogger("添加徽章")
    @SaCheckPermission("activity:badge:add")
    public Result<Long> add(@RequestBody ShopActivityBadge badge) {
        shopActivityBadgeService.insert(badge);
        // save() 会把自增主键回填到入参实体
        return Result.success(badge.getId());
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改徽章：状态（1启用/0禁用）在这一次整行提交；可空的那几格（文字/图标/目标/券/说明）不传就清掉。"
            + "改动只往后生效：已经发到用户手上的那些行各有三格快照，兑现过的分和券也不回头收回")
    @OperationLogger("修改徽章")
    @SaCheckPermission("activity:badge:update")
    public Result<Void> edit(@RequestBody ShopActivityBadge badge) {
        shopActivityBadgeService.update(badge);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除徽章：只删还没发出去过的那几枚，已经发到用户手上的那些行还指着这一枚定义（三格快照是那一刻的凭证）")
    @OperationLogger("删除徽章")
    @SaCheckPermission("activity:badge:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopActivityBadgeService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 删除获取记录：ids 是明细行的 id（不是用户 id，一个人可以有几枚徽章，一枚一行）
     */
    @DeleteMapping("/record/delete/{ids}")
    @ApiOperation(value = "删除获取记录：把一枚徽章从某个人名下抹掉，只删用户徽章那张表上的这一行，"
            + "当初随徽章一起兑现的积分和券不收回（收分走积分页那颗「修改」、作废券走优惠券页那两条路，各记各的操作人）。"
            + "服务端是物理删除 —— uk_badge_user(badge_id, user_id) 不含软删那一格，留一行 is_deleted=1 "
            + "就等于把这个人这一枚永久封住（自动判定会白撞一次唯一键静默跳过，下发还会报「已经持有」）")
    @OperationLogger("删除获取记录")
    @SaCheckPermission("activity:badge:recordDelete")
    public Result<Void> removeRecord(@PathVariable List<Long> ids) {
        shopActivityBadgeUserService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 改一条获取记录的「状态」：只动 status 这一列（弹窗里那一格的开关，一改就提交）
     */
    @PutMapping("/record/status")
    @ApiOperation(value = "修改获取记录的状态：1 有效、0 失效，只动用户徽章那张表上的 status 一列 —— "
            + "名称三格快照、有效期、是否展示都不碰，也不收积分不收券、不删这一行（失效说的是「这一枚还算不算数」，不是账）。"
            + "2 已过期 那一档已经下线：过期不落这一格，前端拿有效期那一格现比。"
            + "读这一格的是前台和 APP 那两处「这枚徽章现在还算不算数」—— 失效的那枚还在列表里、也还挂在账号卡下面，只是变灰")
    @OperationLogger("修改徽章状态")
    @SaCheckPermission("activity:badge:recordStatus")
    public Result<Void> updateRecordStatus(@RequestBody ShopActivityBadgeUser record) {
        shopActivityBadgeUserService.updateStatus(record.getId(), record.getStatus());
        return Result.success();
    }

    /**
     * 改一条获取记录的「是否展示」：只动 is_show 这一列（弹窗里那一格的开关，一改就提交）
     */
    @PutMapping("/record/show")
    @ApiOperation(value = "修改获取记录的展示状态：1 展示、0 隐藏，只动用户徽章那张表上的 is_show 一列 —— "
            + "名称三格快照、有效期、状态都不碰，也不收积分不收券（这一格说的是「这一枚要不要给人看」，不是账）。"
            + "前台「个人信息 → 徽章」那一列不按这一格筛（收起来的要看得见才能放回来），只有账号卡下面那一排挂件读它")
    @OperationLogger("修改徽章展示")
    @SaCheckPermission("activity:badge:recordShow")
    public Result<Void> updateRecordShow(@RequestBody ShopActivityBadgeUser record) {
        shopActivityBadgeUserService.updateIsShow(record.getId(), record.getIsShow());
        return Result.success();
    }

    @PostMapping("/give")
    @ApiOperation(value = "下发徽章：把一枚徽章手工发给选中的每个用户（不看达标线，已经持有这一枚的自动跳过，所以返回的是实发人数），"
            + "有效期不传就是永久；同一趟把定义表上那两格奖励兑现（积分进账户并记一笔「11 徽章发放」的流水、"
            + "券模板逐个发到用户名下并占各张的发行额度）。整批一个事务：券发不出去就全部回滚，不留半批")
    @OperationLogger("下发徽章")
    @SaCheckPermission("activity:badge:give")
    public Result<Integer> give(@RequestBody ShopActivityBadgeGiveDto form) {
        return Result.success(shopActivityBadgeGrantService.grantToUsers(form));
    }
}
