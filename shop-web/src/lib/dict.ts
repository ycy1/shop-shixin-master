/**
 * 枚举标签走后端字典（sys_dict / sys_dict_data），前台不再自己抄一份。
 *
 * 为什么要有这一份：订单类型那一档改过一次定义（1-普通 2-充值/提现 3-盲盒），
 * 而标签原先写死在 src/i18n/enums.ts 的四语言数组里 —— 改字典得跟着改四份 i18n 再发一次版，
 * 漏一档就出现「库里是 2、页面上写秒杀订单」。走字典之后只有服务端一处真相。
 *
 * 读的是 GET /api/web/dict/{类型,逗号隔开}，返回形状和后台 useDict 吃的那一份完全一样
 * （{ 类型: { defaultValue, list } }），只给 status=1 的那些条。不要登录态。
 */
import { reactive } from 'vue';
import { apiGet } from './api';

export interface DictItem {
  label: string;
  value: string;
}

interface DictEntry {
  defaultValue: string | null;
  list: DictItem[];
}

/** 到了的字典。模块级一份：同一趟浏览里好几个岛要用同一本，只发一次请求 */
const loaded = reactive<Record<string, DictEntry | undefined>>({});

/** 已经要过的类型（不管回没回来），挡住重复请求 */
const requested = new Set<string>();

/**
 * 要几本字典。没到期的那些调用方先按「还没到」处理（dictReady 为假），
 * 数据落进来后 reactive 会自己把那一格重算一遍，不用调用方等返回值。
 * 失败就把这一批退回「没要过」，下次挂载还能再要。
 */
export function loadDict(types: string[]): Promise<void> {
  const todo = types.filter(type => !requested.has(type));
  if (todo.length === 0) return Promise.resolve();
  todo.forEach(type => requested.add(type));
  return apiGet<Record<string, DictEntry>>(`/api/web/dict/${todo.join(',')}`)
    .then((data) => {
      Object.entries(data || {}).forEach(([type, entry]) => {
        loaded[type] = entry;
      });
    })
    .catch(() => {
      todo.forEach(type => requested.delete(type));
    });
}

/** 这本字典到没到 —— 没到之前别急着报「未知」，先给占位，否则页面会闪一下错的说法 */
export function dictReady(type: string): boolean {
  return loaded[type] !== undefined;
}

/** 这一档的标签；值本身为空、或字典里压根没这一档，都回 null 让调用方自己兜 */
export function dictLabel(type: string, value: number | string | null | undefined): string | null {
  if (value === null || value === undefined) return null;
  const entry = loaded[type];
  if (!entry) return null;
  return entry.list.find(item => String(item.value) === String(value))?.label ?? null;
}
