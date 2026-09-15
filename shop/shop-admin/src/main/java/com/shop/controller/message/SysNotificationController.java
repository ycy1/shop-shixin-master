package com.shop.controller.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.common.Result;
import com.shop.entity.SysNotifications;
import com.shop.service.SysNotificationService;
import com.shop.vo.notifications.NotificationsListVo;
import com.shop.vo.notifications.NotificationsReceiverVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息通知管理
 *
 * @author: quequnlong
 * @date: 2026/8/21
 * @description:
 */
@RestController
@Api(tags = "消息通知管理")
@RequestMapping("/sys/notification")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService sysNotificationService;

    @GetMapping("/list")
    @ApiOperation(value = "获取消息通知列表")
    public Result<IPage<NotificationsListVo>> list(SysNotifications notifications) {
        return Result.success(sysNotificationService.selectPage(notifications));
    }

    @PutMapping("/update")
    @ApiOperation(value = "修改消息通知")
    @SaCheckPermission("sys:notification:update")
    public Result<Void> update(@RequestBody SysNotifications notifications) {
        sysNotificationService.update(notifications);
        return Result.success();
    }

    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "删除消息通知（逻辑删除）")
    @SaCheckPermission("sys:notification:delete")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysNotificationService.deleteBatch(ids);
        return Result.success();
    }

    @GetMapping("/receiver/list")
    @ApiOperation(value = "查询消息阅读记录（分页，可按阅读人搜索）")
    public Result<IPage<NotificationsReceiverVo>> receiverList(@RequestParam Long notificationId,
                                                               @RequestParam(required = false) String keyword) {
        return Result.success(sysNotificationService.listReceivers(notificationId, keyword));
    }

    @PutMapping("/receiver/read")
    @ApiOperation(value = "阅读记录标记已读")
    @SaCheckPermission("sys:notification:update")
    public Result<Void> receiverRead(@RequestBody List<Long> ids) {
        sysNotificationService.updateReceiversRead(ids);
        return Result.success();
    }

    @PutMapping("/receiver/delete")
    @ApiOperation(value = "删除阅读记录（该接收人隐藏消息）")
    @SaCheckPermission("sys:notification:delete")
    public Result<Void> receiverDelete(@RequestBody List<Long> ids) {
        sysNotificationService.updateReceiversDeleted(ids);
        return Result.success();
    }
}
