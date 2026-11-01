package com.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.entity.ShopPlazaLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 广场消息点赞 Mapper接口
 *
 * 只有 BaseMapper 那几个方法够用，没有自定义 SQL：
 * 赞 = insert 一行（靠 uk_message_user 拦重复，捕 DuplicateKeyException 当「已经赞过」）、
 * 取消赞 = 按 (message_id, user_id) delete 那一行（硬删，同 sys_comment_like 那一路）。
 * 计数不在这里维护：广场上的「点赞数」由 ShopPlazaMessageMapper.xml 现算，所以这张表不需要同一条 count SQL。
 */
@Mapper
public interface ShopPlazaLikeMapper extends BaseMapper<ShopPlazaLike> {
}
