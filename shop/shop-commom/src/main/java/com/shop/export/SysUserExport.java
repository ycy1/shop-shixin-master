package com.shop.export;

import com.shop.annotation.DictFormat;
import com.shop.entity.SysDept;
import com.shop.enums.UserStatusEnum;
import com.shop.config.fesod.CustomConverter;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnore;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.format.DateTimeFormat;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;
import org.apache.fesod.sheet.annotation.write.style.ContentRowHeight;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ContentRowHeight(100)
public class SysUserExport implements Serializable {

//    @ExcelProperty(value = "用户名", converter = CustomConverter.StringConverter.class)
//    @ColumnWidth(18)  // 宽度设为20个字符
//    private String username;

    @ExcelProperty(value = "昵称")
    @ColumnWidth(20)
    private String nickname;

    @ExcelProperty(value = "性别", converter = CustomConverter.DicConverterToInt.class)
    @DictFormat(code = "sys_user_sex") // 声明字典类型
    @ColumnWidth(10)
    private Integer sex;

    @ExcelProperty(value = "用户标签")
    @ColumnWidth(20)
    private String userTags;

    @ExcelProperty(value = "部门", converter = CustomConverter.ListConverter.class)
    @ColumnWidth(20)
    private List<String> deptNames;

//    @ExcelProperty(value = "部门Id", converter = CustomConverter.ListConverter.class)
//    @DictFormat(dbClazz = SysDept.class)// 声明字典类型
//    private List<Long> deptIds;

    @ExcelProperty(value = "中文地址")
    @ColumnWidth(25)
    private String areaZh;

    @ExcelProperty(value = "状态", converter = CustomConverter.DicConverterToInt.class)
    @DictFormat(enumType = UserStatusEnum.class) // 声明字典类型
    @ColumnWidth(10)
    private Integer status;

    @ExcelProperty(value = "上次登录时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(20)
    private LocalDateTime lastLoginTime;

//    @ExcelProperty(value = "头像", index = 12)
//    @ColumnWidth(50)
//    private String avatar;

//    @ExcelProperty(value = "二维码", index = 14)
//    @ColumnWidth(50)
    @ExcelIgnore
    private String qrImg;

    @ExcelProperty(value = "二维码")
    @ColumnWidth(20)
    private File qrImgFile;

    @ExcelProperty(value = "手机号")
    @ColumnWidth(15)
    private String mobile;

    @ExcelProperty(value = "邮箱")
    @ColumnWidth(25)
    private String email;

    @ExcelProperty(value = "登录方式", converter = CustomConverter.DicConverterToStr.class)
    @DictFormat(data = "wechat=微信,email=邮箱,applet=小程序,account=账号") // 声明字典类型
    @ColumnWidth(12)
    private String loginType;

    @ExcelProperty(value = "创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ColumnWidth(20)
    private LocalDateTime createTime;

}
