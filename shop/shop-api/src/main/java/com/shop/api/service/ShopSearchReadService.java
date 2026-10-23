package com.shop.api.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.vo.activity.ShopActivityDetailWebVo;
import com.shop.vo.activity.ShopActivityWebVo;
import com.shop.vo.brand.ShopBrandWebVo;
import com.shop.vo.category.ShopCategoryWebVo;
import com.shop.vo.notice.NoticeDetailWebVo;
import com.shop.vo.store.ShopStoreWebVo;

import java.util.List;

/**
 * 门店 / 品牌 / 分类读 服务接口（Web 前台：搜索结果页那两块 + 门店详情页 + 品牌详情页 + 首页筛选栏的分类候选）
 *
 * 为什么写在 shop-api：和商品前台列表一路（ShopProductReadService），都是只有 Web 一个使用方的读链路。
 * 不复用后台的 ShopStoreService / ShopBrandService.selectPage：那两条第一句就按账号类型（=门店）收窄数据范围、
 * 按 status DESC 把禁用的也一并列给运营看，返回的还是带审计字段的实体，三样都不属于前台。
 * 几条一起放这一个服务里：门店列表和门店详情是同一份 VO、同一档判据（只看 status=1），各开一个接口类没必要。
 */
public interface ShopSearchReadService {

    /**
     * 分页搜门店。只看启用的（status=1），营业状态不参与筛选 —— 休息中的店照样要能搜出来，
     * 由页面把「休息中 / 已打烊」标出来。
     *
     * @param keyword 名称/编码/地址关键字，空就是不限
     */
    IPage<ShopStoreWebVo> pageStores(String keyword);

    /**
     * 单个门店的详情（门店详情页头部和那一圈图片）。同样只看启用的，禁用中的店前台进不去。
     *
     * @param id 门店id
     * @throws com.shop.exception.ServiceException 门店不存在、已删除或已停用
     */
    ShopStoreWebVo storeDetail(Long id);

    /**
     * 分页搜品牌。只看启用的（status=1）。
     *
     * @param keyword 品牌名称关键字，空就是不限
     */
    IPage<ShopBrandWebVo> pageBrands(String keyword);

    /**
     * 单个品牌的详情（品牌详情页头部 + 代言人那一圈图片）。同样只看启用的，停用中的品牌前台进不去。
     * 代言人是 json 串，这一层不拆（同门店 coverImages），由页面自己解析。
     *
     * @param id 品牌id
     * @throws com.shop.exception.ServiceException 品牌不存在、已删除或已停用
     */
    ShopBrandWebVo brandDetail(Long id);

    /**
     * 一个品牌名下的门店（品牌详情页的「品牌门店」那一块）。
     *
     * 认的是 shop_store.brand_id 那一列 —— 门店表单里「绑定品牌」手工挂上去的，一条等值查询就够，不联表。
     * （2026-10-05 前这里绕的是 shop_product：数「哪个店在卖这个品牌的货」。那条线和这一列不是一回事，
     * 没绑品牌的店也可能在卖某品牌的货，两块迟早对不上，所以改读门店自己挂的那一格。）
     * 门店侧同列表那条判据（只看启用中的），按后台门店列表的排法（sort 升、新在前）给。
     * 品牌不存在或名下没门店都回空列表，不另分一档错误：这一块的空白本来就是合法状态。
     *
     * @param brandId 品牌id
     */
    List<ShopStoreWebVo> brandStores(Long brandId);

    /**
     * 分类筛选栏的候选（首页「全部商品」按分类筛那一格）。
     *
     * 只看启用中的（status=1）；一次给全、不分页 —— 这一条是下拉框的候选，分页会把候选切没。
     * 顺序是「父在前、自己的下级紧跟其后」：库里只有 parent_id 和 level（没有 path 列），
     * 所以先把启用行按后台列表的排法（sort 升、id 升）排好，再从根往下走一遍。
     * 父被禁用或已删的那一条当根接（不然它的下级会跟着整支从候选里消失）。
     *
     * 和商品列表那条的口径差一点：按分类筛商品时展开下级是不看分类状态的（禁用的下级里、
     * 上架的商品照样能出现在结果里），但候选这一列只摆启用中的 —— 禁用的分类不该出现在下拉框里让人选。
     */
    List<ShopCategoryWebVo> listCategories();

    /**
     * 首页那一块活动轮播要的那几条（`/api/web/shop/search/activities`）。
     *
     * 三道判据，和后台活动管理那一页对得上：`position_type = 1`（首页轮播那一档活动位）、
     * `status = 2`（投放中 —— 这是后台那条链上唯一算「正在投」的判据，投放起止那两格只是表单里的备注，
     * 全仓没有任何地方拿它们和当前时间比过，所以这一条也不自己加第二条判据）、
     * `image` 非空（一张图都没有的活动在这块横幅轨道上画不出格）。
     * 适用门店那一格不参与筛选：它是「这条投给哪家店」的留痕，不是可见性闸门，
     * 首页本来就是全平台那一屏，专场活动也照样给（哪天要做到店级可见性再从这一格收）。
     * 排序照后台列表那套（weight 大的在前、同权重新的在前）。不分页：轮播位一次给全，
     * 分页会把轨道上的格数切没（同 listCategories 那一条理由）。
     */
    List<ShopActivityWebVo> listHomeActivities();

    /**
     * 活动列表页那一屏（`GET /api/web/shop/activity/list`，分页吃查询串里的 pageNum/pageSize）。
     *
     * 和首页轮播那条只差两点：
     * 1）不按 `position_type` 收 —— 这一页是「最近都在投的活动」，首页轮播位、Banner、弹窗那些位的活动一起列；
     * 2）不要求 `image` 非空 —— 文字类活动本来就没配图，卡片自己垫一块无图占位，比在列表里凭空少一条好。
     * 判据还是那一道 `status = 2`（投放中）：投放起止那两格只是表单里的备注，全仓没有拿它们和当前时间比过，
     * 这一条也不自己加第二条判据（同 listHomeActivities）。排序照后台列表那套（weight 大的在前、同权重新的在前）。
     * 适用门店那一格不参与筛选：它是「这条投给哪家店」的留痕，不是可见性闸门。
     *
     * @param keyword 名称或标题关键字（搜索页那个「活动」tab 传的），空就是不限
     */
    IPage<ShopActivityWebVo> pageActivities(String keyword);

    /**
     * 单条活动的详情（`GET /api/web/shop/activity/{id}`，前台活动详情页）。
     *
     * 判据和列表那条一样（只认 status=2），查不到就报错 —— 从列表点进去的活动被人撤了也是这句话。
     * 只有这一条带 content（富文本），所以它用的是另一份 VO，理由写在 ShopActivityDetailWebVo 头上。
     *
     * @param id 活动id
     * @throws com.shop.exception.ServiceException 活动不存在、已删除、或没在投放中
     */
    ShopActivityDetailWebVo activityDetail(Long id);

    /**
     * 单条公告的详情（`GET /api/web/shop/notice/{id}` 与 App 同形那一条，前台公告详情页）。
     *
     * 公告在后台是「发给谁」那一串推送范围 + 一条消息，前台本来没有它的页面；这一条是把消息里
     * 那条「公告通知【…】」点开之后能看全文的路给出来，所以判据只认 `is_show = 1`（后台上下线那颗开关），
     * 不再叠第二条 —— 已下线的公告留在库里给历史记录看，但前台进不去。
     * 没这一格（不存在、已删、已下线）回同一句话，不从错误信息里试哪个 id 存在过，同活动/门店/品牌详情那条口径。
     *
     * @param id 公告id（sys_notice.id，也就是那条消息的 businessId）
     * @throws com.shop.exception.ServiceException 公告不存在或已停止展示
     */
    NoticeDetailWebVo noticeDetail(Long id);
}
