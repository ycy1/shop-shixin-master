package com.shop.vo.trendy;

import io.swagger.annotations.ApiModel;
import lombok.Data;

/**
 * 园区照片 Web 端视图（园区详情那一圈图集）
 *
 * 只给审核通过的（判据在服务层）：待审核、被拒、被隐藏的图不该出现在给游客看的那一页上。
 * 来源、上传人、排序这些后台字段不外给（口径同园区、票种那两份 WebVo）。
 */
@Data
@ApiModel(value = "园区照片 Web 端视图")
public class ShopParkPhotoWebVo {

    private Long id;

    private String imageUrl;

    /** 图注一句话（后台那格 description，最多 64 字）；没填就是 null，页面不画那一行 */
    private String description;
}
