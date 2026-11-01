package com.shop.enums;

/**
 * 消息通知的业务来源十二档：sys_notifications.business_type 这一格的取值，和字典 notification_business_type 一字不差。
 *
 * 为什么要一个枚举：category（系统消息 / 互动消息）是这一格的派生数，前台消息中心两档 tab 靠它分；
 * 挂载点（评论、点赞、公告、活动开始、培训开始、签收、徽章、广场审核结果、IP授权到期、潮玩集齐、园区照片审核结果）
 * 各写各的 1/2 迟早写岔，所以判据只留在这里一份，落库那一趟由 NotificationsUtil 现取。
 */
public enum NotificationTypeEnum {

    /** 后台手工发的通知 */
    SYSTEM("system", 1),

    /** 公告（sys_notice 发布/修改/上下线那三处） */
    NOTICE("notice", 1),

    /** 别人回复了我的评论 */
    COMMENT("comment", 2),

    /** 别人赞了我的评论 */
    LIKE("like", 2),

    /** 活动到点开始（shop_activity.start_time） */
    ACTIVITY("activity", 1),

    /** 门店培训到点开始（shop_store_training.start_time） */
    TRAINING("training", 1),

    /**
     * IP授权临到期（shop_trendy_ip.license_end 前一个月，定时扫描发给版权人/授权人那一格的人）：
     * 走 1 系统消息 —— 这是平台在替运营盯合同，不是某个同事来搭话。
     */
    LICENSE("license", 1),

    /** 订单签收（前台点确认收货） */
    RECEIVE("receive", 1),

    /** 领到徽章（shop_activity_badge_user 落行） */
    BADGE("badge", 1),

    /**
     * 广场消息的审核结论（shop_plaza_message 审核那一拍）：
     * 走 1 系统消息而不是 2 互动消息 —— 那是平台替作者拿的主意，不是另一个人过来搭话，
     * 挂进「互动」那一档会让人以为有人看了他写的东西。
     */
    PLAZA("plaza", 1),

    /**
     * 集齐一条潮玩系列（shop_trendy_collect_record 落行那一刻）：走 1 系统消息 ——
     * 那是平台按系列配置兑现的奖励，跟"有人看了他写的东西"没关系，口径同徽章那一档。
     */
    COLLECT("collect", 1),

    /**
     * 园区照片的审核结论（shop_trendy_park_photo 审核那一拍，只发给**用户上传**那一路的上传人）：
     * 走 1 系统消息，口径同广场审核结果 —— 图能不能上墙是平台替作者拿的主意。
     */
    PARK_PHOTO("park_photo", 1);

    /** 1-系统消息 2-互动消息（字典 notification_category） */
    public static final int CATEGORY_SYSTEM = 1;
    public static final int CATEGORY_INTERACT = 2;

    private final String code;

    private final int category;

    NotificationTypeEnum(String code, int category) {
        this.code = code;
        this.category = category;
    }

    /** 落进 sys_notifications.business_type 的那串字 */
    public String getCode() {
        return code;
    }

    /** 落进 sys_notifications.category 的那一格 */
    public int getCategory() {
        return category;
    }

    /**
     * 按 business_type 那串字认档；认不出（后台表单里手工填的脏值）就当「后台通知」，
     * 只为把 category 那一格钉成有值的数 —— 前台两档 tab 的谓词吃的是 category，不能留 NULL。
     */
    public static NotificationTypeEnum of(String code) {
        for (NotificationTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return SYSTEM;
    }
}
