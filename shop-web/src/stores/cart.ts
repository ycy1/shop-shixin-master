import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import {
  addMyCart,
  deleteMyCart,
  fetchMyCart,
  selectAllMyCart,
  updateMyCart,
  type CartLine,
} from '@/lib/cart';

/**
 * 购物车是库里那一份（shop_consumer_cart，一行 = 一个规格），不再落 localStorage。
 *
 * 换过来的理由是那张表本来就是给前台车的（user_id 存的是购物车归属的消费者，不是创建人），
 * 后台「购物车管理」那一页读的是同一批行 —— 两边各一套车的话，客服代客看的和自己看到的就对不上了。
 * 代价是加购要登录：没登录时详情页那颗按钮挂的是 data-open-login，点了开登录窗（口径同收藏那颗）。
 *
 * island 之间不共享 store（astro 每个岛一个 Vue 应用），所以这一份是「每个挂到它的岛各一份」：
 * 谁要用车谁自己 load 一次。同一颗岛内部的几块（详情页的按钮 + 那一岛里的 CartBadge）用的还是同一份，
 * 所以加完购badge 会跟着翻 —— 跨岛同步没做，也不需要：换页时新页面上的岛是新建的，那时候自然读到最新的。
 *
 * 写法一律「先改本地、再发请求、失败就重拉」：勾选和步进点下去必须立刻有反馈，
 * 而单价、库存、可购状态都以库里那份为准，出错时唯一靠得住的就是回服务端重读一遍。
 */
export const useCartStore = defineStore('cart', () => {
  const lines = ref<CartLine[]>([]);
  const loading = ref(false);
  /** 整块级别的错误（列表没捞动）；单颗写的失败走各自抛出来的那句，由调用方就地印 */
  const error = ref('');

  function priceOf(line: CartLine): number {
    return Number(line.productPrice ?? 0);
  }

  /** 还能买的那几行：商品下架的不算进份数也不算进金额（badge 上那两个数说的是「还要买多少」） */
  const buyableLines = computed(() => lines.value.filter((line) => line.buyable === 1));
  const count = computed(() => buyableLines.value.reduce((sum, line) => sum + line.quantity, 0));
  const total = computed(() =>
    buyableLines.value.reduce((sum, line) => sum + priceOf(line) * line.quantity, 0),
  );

  /** 选中的那几行才是「将要下单」的那一份，合计按它算 */
  const selectedLines = computed(() => buyableLines.value.filter((line) => line.selected === 1));
  const selectedCount = computed(() => selectedLines.value.reduce((sum, line) => sum + line.quantity, 0));
  const selectedTotal = computed(() =>
    selectedLines.value.reduce((sum, line) => sum + priceOf(line) * line.quantity, 0),
  );
  /** 全选那颗的勾：按可购的行比（灰行不给勾，所以不能拿它当分母，否则永远勾不上） */
  const allSelected = computed(
    () => buyableLines.value.length > 0 && selectedLines.value.length === buyableLines.value.length,
  );

  async function load() {
    loading.value = true;
    error.value = '';
    try {
      lines.value = await fetchMyCart();
    } catch (err) {
      error.value = (err as Error).message;
    } finally {
      loading.value = false;
    }
  }

  /** 加购之后必须重拉：这一颗可能是建了新的一行，名称/单价/规格都由服务端联出来，前端手上根本没有那些数 */
  async function add(skuId: number, quantity = 1) {
    await addMyCart(skuId, quantity);
    await load();
  }

  async function setQuantity(id: number, quantity: number) {
    const hit = lines.value.find((line) => line.id === id);
    if (!hit || quantity < 1) return;
    const before = hit.quantity;
    hit.quantity = quantity;
    try {
      await updateMyCart({ id, quantity });
    } catch (err) {
      hit.quantity = before;
      throw err;
    }
  }

  async function setSelected(id: number, selected: 0 | 1) {
    const hit = lines.value.find((line) => line.id === id);
    if (!hit) return;
    const before = hit.selected;
    hit.selected = selected;
    try {
      await updateMyCart({ id, selected });
    } catch (err) {
      hit.selected = before;
      throw err;
    }
  }

  /** 全选/全不选一次改本地所有可购行：灰行不动（它们本来就不参与合计，勾了也是白勾） */
  async function setAllSelected(selected: 0 | 1) {
    const touched = buyableLines.value.map((line) => [line, line.selected] as const);
    touched.forEach(([line]) => {
      line.selected = selected;
    });
    try {
      await selectAllMyCart(selected);
    } catch (err) {
      touched.forEach(([line, before]) => {
        line.selected = before;
      });
      throw err;
    }
  }

  async function remove(ids: number[]) {
    if (!ids.length) return;
    const before = lines.value;
    lines.value = lines.value.filter((line) => !ids.includes(line.id));
    try {
      await deleteMyCart(ids);
    } catch (err) {
      lines.value = before;
      throw err;
    }
  }

  return {
    lines,
    loading,
    error,
    buyableLines,
    count,
    total,
    selectedLines,
    selectedCount,
    selectedTotal,
    allSelected,
    load,
    add,
    setQuantity,
    setSelected,
    setAllSelected,
    remove,
  };
});
