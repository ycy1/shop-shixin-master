package com.shop.common;

/**
 * Redis常量
 */
public class RedisConstants {

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN = "login:token:";

    /**
     * 用户签到
     */
    public static final String USER_SIGN = "user_sign:";


    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_code:";

    /**
     * 滑块验证码 redis key
     */
    public static final String SLIDER_CAPTCHA_CODE_KEY = "slider_captcha_code:";

    /**
     * 访客
     */
    public static final String UNIQUE_VISITOR = "unique_visitor";

    /**
     * 访客量
     */
    public static final String UNIQUE_VISITOR_COUNT = "unique_visitor_count";

    /**
     * 博客浏览量
     */
    public static final String BLOG_VIEWS_COUNT = "blog_views_count";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";


    /**
     * 过期时间 1分钟
     */
    public static final long MINUTE_EXPIRE = 60;

    /**
     * 过期时间 5分钟
     */
    public static final long FIVE_MINUTES_EXPIRE = 5 * 60;

    /**
     * 过期时间 30分钟
     */
    public static final long HALF_HOUR_EXPIRE = 30 * 60;

    /**
     * 过期时间 1小时
     */
    public static final long HOUR_EXPIRE = 60 * 60;

    /**
     * 过期时间 1天
     */
    public static final long DAY_EXPIRE = 24 * 60 * 60;

    /**
     * 过期时间 7天
     */
    public static final long WEEK_EXPIRE = 7 * 24 * 60 * 60;

    /**
     * 过期时间 15天
     */
    public static final long FIFTEEN_DAYS_EXPIRE = 15 * 24 * 60 * 60;

    /**
     * 过期时间 30天
     */
    public static final long MONTH_EXPIRE = 30 * 24 * 60 * 60;


    /**
     * 微信登录用户状态
     */
    public static final String WX_LOGIN_USER_CODE = "wx_login_user_statue:";

    /**
     * 微信登录用户信息
     */
    public static final String WX_LOGIN_USER = "wx_login_user:";

    /**
     * 扫码登录状态（值：待扫码/已扫码待确认/已确认/已取消）
     */
    public static final String QR_LOGIN = "qr_login:";

    /**
     * 文章阅读量
     */
    public static final String ARTICLE_QUANTITY = "article_quantity";

    /**
     * 网站配置
     */
    public static final String WEB_CONFIG_KEY = "sys_webconfig";

    /**
     * 首页那一排热搜（一整个 ZSet：成员=关键字，分值=这个关键字被搜的次数）
     */
    public static final String SEARCH_HOT_KEY = "search_hot";

    /**
     * 园区照片的标签词表（一整个 Set：成员=一个标签文本）
     *
     * 后台「园区照片」新建/修改表单里那颗下拉列的就是这一串 —— 不是字典、也不是当前列表里数出来的，
     * 存这儿是因为它要的就是「一处维护、到处都能选」：照片删了词还在（词表不该跟着图一起缩），
     * 新打的词落库时顺手进这一串，下一次新建就能选到。
     * 这一串空掉（从没建过 / 被清库）时，服务端会从库里已在用的标签先灌一遍当起点。
     */
    public static final String PARK_PHOTO_TAG_KEY = "park_photo_tag";
}
