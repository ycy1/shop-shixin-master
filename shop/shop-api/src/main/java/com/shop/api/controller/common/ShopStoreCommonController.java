package com.shop.api.controller.common;

import com.shop.api.service.ShopTrainingSignService;
import com.shop.common.Result;
import com.shop.controller.BaseCommController;
import com.shop.dto.training.ShopTrainingSignDto;
import com.shop.entity.ShopStore;
import com.shop.vo.training.ShopTrainingAttendVo;
import com.shop.vo.training.ShopTrainingSignVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author xxj
 * @title ShopPayCommonController
 * @date 2026/9/19 22:25
 */
@Api(tags = "store 公共接口")
@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class ShopStoreCommonController extends BaseCommController {

    private final ShopTrainingSignService shopTrainingSignService;

    /**
     * 培训报名：门店那边给自己报一场培训，在 shop_store_training_member 里落一行 1 已报名。
     *
     * 路径：继承 BaseCommController 会被 WebMvcConfig 加上 /api/comm 前缀，即 POST /api/comm/store/training/sign
     * （前端调 comm 要写双前缀，request 的 baseURL 会再叠一层 /api）。
     * /api/** 不过 Sa-Token 拦截器，所以这一条也没有 @SaCheckPermission —— 资格判的是登录态
     * 加"这个账号挂在哪几家门店上"，哪一场收报名、名额怎么算，全在 ShopTrainingSignServiceImpl 的类注释里。
     *
     * 和「门店培训」列表页那几颗读接口是两条路：这一条只管写报名那一行，
     * 培训本身的状态、已报名人数都不由它改（人数是读的时候现数的）。
     */
    @PostMapping("/training/sign")
    @ApiOperation(value = "培训报名（本人报名：校验培训在报名中、没超名额，写一行 1 已报名并回现数的人数）")
    public Result<ShopTrainingSignVo> signTraining(@RequestBody ShopTrainingSignDto form) {
        return Result.success(shopTrainingSignService.sign(form));
    }

    /**
     * 「可报名门店」：当前账号名下能拿来报名的那几家店（绑定账号 OR 店长，判据和上面那条报名一模一样）。
     *
     * 路径：GET /api/comm/store/training/sign/stores。「门店培训」列表页进页面时取一次，
     * 拿它比对每一行自己的「所属门店」在不在名下 —— 在，那几行才出「报名」那颗按钮
     * （一家都没有的账号，比如平台/ERP/消费者，那一列整片都不出）。门店不由人挑：报的就是这一场所属的那家。
     * 不在报名范围返回空列表，不是错误。
     */
    @GetMapping("/training/sign/stores")
    @ApiOperation(value = "可报名门店（当前账号名下的门店，空列表即不在报名范围）")
    public Result<List<ShopStore>> signStores() {
        return Result.success(shopTrainingSignService.signStores());
    }

    /**
     * 培训签到：把自己那一行报名记录的出勤状态翻成 1 已签到，记签到时间。
     *
     * 路径：POST /api/comm/store/training/attend/{trainingId}（前缀同上）。
     * 只有路径里那一个培训 id，没有 user_id 这一格 —— 能签的只有自己报过的那一行，
     * 参数能点名别人就等于替别人签到。为什么不看培训是不是"3 进行中"，见 ShopTrainingSignServiceImpl 的类注释。
     */
    @PostMapping("/training/attend/{trainingId}")
    @ApiOperation(value = "培训签到（本人签到：校验已报名且培训未取消，写 attend_status=1 并记签到时间）")
    public Result<ShopTrainingAttendVo> attendTraining(@PathVariable("trainingId") Long trainingId) {
        return Result.success(shopTrainingSignService.attend(trainingId));
    }
}
