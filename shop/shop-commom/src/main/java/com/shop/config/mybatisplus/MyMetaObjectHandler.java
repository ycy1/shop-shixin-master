package com.shop.config.mybatisplus;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.shop.common.Constants;
import com.shop.dto.user.LoginUserInfo;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LoginUserInfo loginUserInfo = getCurrentUser();
        // 用 setFieldValByName 而不是 strictInsertFill：strict 系列只填 null，
        // 而后台表单是 Object.assign(form, data) 灌进来的，reset() 又只重置声明过的键，
        // 上一次编辑残留的 createBy/updateBy 会跟着新增请求一起提交。
        // 快照里的 id 和 createBy/updateBy 同宽（都是 Long），直接取
        if (loginUserInfo != null && loginUserInfo.getId() != null) {
            this.setFieldValByName("createBy", loginUserInfo.getId(), metaObject);
            this.setFieldValByName("updateBy", loginUserInfo.getId(), metaObject);
        }
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LoginUserInfo loginUserInfo = getCurrentUser();
        // 这里不能用 strictInsertFill/strictUpdateFill：它们只在字段仍为 null 时才填，
        // 而后台表单是把查出来的实体原样回传的，updateBy 永远非空、修改人就会被上一个值顶掉。
        // setFieldValByName 内部有 hasSetter 判断，没有该属性的实体不受影响。
        if (loginUserInfo != null && loginUserInfo.getId() != null) {
            this.setFieldValByName("updateBy", loginUserInfo.getId().longValue(), metaObject);
        }
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }

    private LoginUserInfo getCurrentUser() {
        try {
            Object obj = StpUtil.getSession().get(Constants.CURRENT_USER);
            if (obj instanceof LoginUserInfo) {
                return (LoginUserInfo) obj;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
} 