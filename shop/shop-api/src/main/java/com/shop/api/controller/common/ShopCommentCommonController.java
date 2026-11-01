package com.shop.api.controller.common;

import com.shop.api.service.ShopCommentWriteService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.comment.ShopCommentSaveDto;
import com.shop.entity.SysComment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ycy
 * @title ShopCommentCommonController
 * @description 评论公共接口（前台两端都会做的三个动作：发表/回复、点赞、删自己那一条）
 */
@Api(tags = "评论 公共接口")
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopCommentCommonController extends BaseCommController {

    private final ShopCommentWriteService shopCommentWriteService;

    /**
     * 发表评论 / 回复一条：落到 sys_comment 里一行，回复挂在被回复那一行下面，层数不限。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，
     * 即 POST /api/comm/shop/comment/publish（前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * 这一条 Web 和 App 共用，所以不放 web/app 任何一包：同一个动作在两处写两遍判据，早晚对不上。
     *
     * 参数里没有 userId 那一格 —— 能发的只有自己（登录态由 service 自己取，/api/** 不过 Sa-Token 拦截器，
     * 取不到就报「请先登录后再发表评论」）。传 replyToId 就是回复，
     * 那一趟连评论类型和对象都跟着被回复那一条走，省得前端把回复挂到别处。
     *
     * 回刚落库的那一行：页面上那句「发表成功」和之后重新捞的那一页都对得上号，
     * 但昵称/头像不在这一行里（那是联 sys_user 现带的），所以前端发完还是回读一次列表。
     */
    @PostMapping("/comment/publish")
    @ApiOperation(value = "发表评论/回复（本人发，内容非空且不超 500 字；传 replyToId 即回复，回刚落库的那一行）")
    public Result<SysComment> publish(@RequestBody ShopCommentSaveDto form) {
        return Result.success(shopCommentWriteService.publish(form));
    }

    /**
     * 点赞那颗按钮：一颗管两个方向，点过就取消、没点过就点上，回按完之后的状态。
     *
     * 路径：POST /api/comm/shop/comment/like/{id}（按评论 id 点，一人一次由库里那道唯一键保证）。
     * 必须登录 —— 点赞记的是「哪个人点的」，游客没有这一格可记；
     * 未登录点它，前端挂的是 data-open-login（同那颗收藏按钮），根本走不到这里。
     * 两句写（那一行 + like_count 那一格）在同一个事务里，判据全在 ShopCommentWriteServiceImpl 的类注释里。
     */
    @PostMapping("/comment/like/{id}")
    @ApiOperation(value = "点赞/取消点赞（本人一次，回按完之后的状态：true 已点赞 false 已取消）")
    public Result<Boolean> toggleLike(@PathVariable("id") Integer id) {
        return Result.success(shopCommentWriteService.toggleLike(id));
    }

    /**
     * 删自己发的那一条：软删，名下回复一并收掉，已收到的点赞行不回收。
     *
     * 路径：POST /api/comm/shop/comment/delete/{id}。
     * 只有作者本人删得到 —— 后台那颗删除走的是另一条（/sys/comment/delete/{ids}，压 sys:comment:delete 权限），
     * 这一条不鉴权，所以归属那道闸必须钉在服务层：参数能点名别人的 id，不比自己就能替别人删评论。
     */
    @PostMapping("/comment/delete/{id}")
    @ApiOperation(value = "删除本人评论（软删；顶层那一条连名下回复一起删）")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        shopCommentWriteService.delete(id);
        return Result.success();
    }
}
