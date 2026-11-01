package com.shop.controller.site;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.annotation.OperationLogger;
import com.shop.common.Result;
import com.shop.dto.plaza.ShopPlazaAuditDto;
import com.shop.entity.ShopPlazaMessage;
import com.shop.service.ShopPlazaMessageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 广场消息（站点管理 -> 广场消息）
 *
 * 读接口按项目惯例只做登录校验（Sa-Token 拦截器兜全路径），权限只压在审核/修改/删除上。
 *
 * 这一页没有 add：广场上的消息都是前台用户发的（两端共用那一条 POST /api/comm/shop/plaza/publish），
 * 落库一律是 0 待审核，所以这个列表要看的正是待审核和已下架的那几档 —— 不按 onlyPublic 收窄。
 *
 * 「审核」单独一条端点而不是塞进 update：判成 1 已发布那一刻要同一趟补 publish_time、送 10 积分、
 * 记一笔积分流水，这三件是审核这个动作独有的，混在修改里就没法说清哪一次改动会送分。
 */
@Api(tags = "广场消息")
@RestController
@RequestMapping("/sys/plaza")
@RequiredArgsConstructor
public class ShopPlazaMessageController {

    private final ShopPlazaMessageService shopPlazaMessageService;

    @GetMapping("/list")
    @ApiOperation(value = "获取广场消息列表（发布者昵称/关联对象名联表带出，点赞/评论/分享三个计数按明细表现算，不含正文）；"
            + "未删除和已删除的混在一页上返回（服务层把 isDeleted 钉成 null），已删除那几条由前端「状态」那一格认")
    public Result<IPage<ShopPlazaMessage>> list(ShopPlazaMessage query) {
        return Result.success(shopPlazaMessageService.selectPage(query));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取一条广场消息详情（带正文，只读详情弹窗、审核弹窗和修改弹窗共用；已删除的那一条后台也读得回来）")
    public Result<ShopPlazaMessage> getInfo(@PathVariable("id") Long id) {
        return Result.success(shopPlazaMessageService.selectPlazaById(id));
    }

    @PutMapping("/audit")
    @ApiOperation(value = "审核广场消息：判 1 已发布 或 2 已拒绝；"
            + "第一次判通过时服务端补 publish_time 并给发布者 10 积分，同一事务落账")
    @OperationLogger("审核广场消息")
    @SaCheckPermission("sys:plaza:audit")
    public Result<Void> audit(@RequestBody ShopPlazaAuditDto form) {
        shopPlazaMessageService.audit(form);
        return Result.success();
    }

    /**
     * 批量审核单独一条端点而不是给上面那一条加一格 ids：这两趟的「读不到该怎么办」正好相反 ——
     * 单条是有人对着这一条做了个决定，读不到必须报错（不然是把结论写进了另一条行上）；
     * 批量勾了十几条，为其中一条已被删掉的把整批结论回滚掉不合理。
     * 权限照用 sys:plaza:audit（同一件事的两种规模，不另开一档），@OperationLogger 单独记一句「批量审核」好翻留痕。
     */
    @PutMapping("/auditBatch")
    @ApiOperation(value = "批量审核广场消息：ids 里那几条一律判成同一档、写同一句备注；"
            + "每条的送分判据与单条审核完全相同，捞不到的那条（已删除/已被别人清掉）跳过")
    @OperationLogger("批量审核广场消息")
    @SaCheckPermission("sys:plaza:audit")
    public Result<Void> auditBatch(@RequestBody ShopPlazaAuditDto form) {
        shopPlazaMessageService.auditBatch(form);
        return Result.success();
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改广场消息：后台动 标题 / 正文 / 广场消息图 / 置顶 / 推荐 / 上下架 六格，"
            + "正文一改摘要跟着重算；发布人、关联对象和审核那几格不由这一趟动，也不重新打回待审核")
    @OperationLogger("修改广场消息")
    @SaCheckPermission("sys:plaza:update")
    public Result<Void> edit(@RequestBody ShopPlazaMessage message) {
        shopPlazaMessageService.update(message);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除广场消息：任何审核状态都能删；名下收到的赞/分享/评论行不回收")
    @OperationLogger("删除广场消息")
    @SaCheckPermission("sys:plaza:delete")
    public Result<Void> remove(@PathVariable List<Long> ids) {
        shopPlazaMessageService.deleteByIds(ids);
        return Result.success();
    }
}
