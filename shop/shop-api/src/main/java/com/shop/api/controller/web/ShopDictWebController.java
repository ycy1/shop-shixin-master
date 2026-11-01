package com.shop.api.controller.web;

import com.shop.common.Result;
import com.shop.controller.BaseWebController;
import com.shop.service.SysDictDataCommService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @author xxj
 * @title ShopDictWebController
 * @description 字典 Web 接口（商城前台按字典类型批量取标签）
 */
@Api(tags = "字典 Web 接口")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class ShopDictWebController extends BaseWebController {

    private final SysDictDataCommService sysDictDataCommService;

    /**
     * 一次可以要好几本字典，类型用逗号隔开：GET /api/web/dict/order_type,pay_type
     *
     * 返回的形状和后台 useDict 吃的那一份完全一样（{ 类型: { defaultValue, list } }），
     * 前台不再自己拼第二份标签表 —— 枚举改了档只改字典，两端一起跟着变。
     *
     * 走的是 SysDictDataCommService（shop-commom 那一份），它按 status=1 过滤；
     * 后台那条 /sys/dictData/getDiceData 用的是 shop-admin 的另一份实现，那一处把状态过滤注释掉了，
     * 所以「停用」在两边表现不一致 —— 前台这份以 commom 那份为准。
     *
     * 放在 /api/web 不放 /api/comm：现在只有前台网页读它（app 端没有显示订单类型这一格），
     * SaTokenConfigure 把 /api/** 排在登录拦截之外，所以不要登录态。
     */
    @GetMapping("/{dictTypes}")
    @ApiOperation(value = "按字典类型批量取字典数据（只给启用中的那些条）")
    public Result<Map<String, Map<String, Object>>> getDictData(@PathVariable List<String> dictTypes) {
        return Result.success(sysDictDataCommService.getDictDataByDictType(dictTypes));
    }
}
