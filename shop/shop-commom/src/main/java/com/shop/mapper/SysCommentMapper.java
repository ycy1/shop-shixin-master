package com.shop.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.dto.message.SysCommentQueryDto;
import com.shop.vo.comment.CommentListVo;
import com.shop.entity.SysComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shop.vo.comment.SysCommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysCommentMapper extends BaseMapper<SysComment> {

    /**
     * 详情页评论区的第一趟：某一对象名下的顶层评论（parent_id IS NULL）分页。
     * businessId 和 commentType 必须一起点 —— 商品 id 会和门店 id 撞号，只比 id 会把别家的评论捞进来。
     */
    IPage<CommentListVo> getComments(@Param("page") Page<Object> page, @Param("businessId") Long businessId,
                                     @Param("sortType") String sortType, @Param("userId") Long userId,
                                     @Param("commentType") Integer commentType);

    /**
     * 上面那一页顶层评论名下的整棵回复树，一次捞完（递归 CTE，IN 一批顶层 id，不逐条逐层查）。
     * 回复可以套多层，所以这一趟回来的不是「一层子项」而是各层混在一条列表里，由 service 按 parentId 堆成树。
     * 子评论不再分页：一个对象下的回复量本就跟着顶层那几条走，前端一次铺完。
     */
    List<CommentListVo> getDescendantsComment(@Param("ids") List<Integer> ids, @Param("userId") Long userId);

    /**
     * 某一条评论名下所有子孙的 id（不含它自己，递归 CTE）：删一行时要连整棵子树一起软删，
     * 不然留下的回复就再也没有顶层能把它带上页面。
     */
    List<Integer> selectDescendantIds(@Param("id") Integer id);

    Page<SysCommentVO> selectPage(@Param("page") Page<SysCommentVO> page, @Param("query") SysCommentQueryDto query);

    IPage<CommentListVo> selectMyComment(@Param("page") Page<Object> page, @Param("userId") long userId);

    IPage<CommentListVo> getMyReply(@Param("page") Page<Object> page, @Param("userId") long userId);

    /**
     * 点赞那一行按 (comment_id, user_id) 有唯一键，重复插会被数据库拦下来 ——
     * 所以「点过没」的判据在 service 里先问一次，这里只负责插。
     * comment_type 这一格是跟着评论自身抄一份进来的留痕，判据不看它。
     */
    void likeComment(@Param("commentId") Integer commentId, @Param("userId") Long userId, @Param("commentType") Integer commentType);

    void unLikeComment(@Param("commentId") Integer commentId, @Param("userId") Long userId);

    Boolean getUserIsLikeComment(@Param("commentId") Integer commentId, @Param("userId") Long userId);

    /** 点赞数是存下来的：插/删 sys_comment_like 之后跟着这条一起动，GREATEST 兜住不减成负数 */
    void updateLikeCount(@Param("commentId") Integer commentId, @Param("delta") int delta);
}
