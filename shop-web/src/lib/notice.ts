import { apiGet } from './api';

/**
 * 公告详情读接口绑定（GET /api/web/shop/notice/{id}）。
 *
 * 只有这一条端点带 content（后台那格富文本原文），判据只认 is_show=1：撤下、删掉、根本没这个 id 回同一句话。
 * 前台没有公告列表页 —— 公告是从消息中心那一条链进来的（lib/notification 的 messageTarget 拼 /notice/{id}），
 * 所以这里不配分页那一套。
 */
export interface NoticeDetail {
  id: number;
  title: string | null;
  /** 富文本 HTML，上屏前必须过 sanitizeRichText（见 lib/richtext.ts） */
  content: string | null;
  createTime: string | null;
}

export function fetchNoticeDetail(id: number): Promise<NoticeDetail> {
  return apiGet<NoticeDetail>(`/api/web/shop/notice/${id}`);
}
