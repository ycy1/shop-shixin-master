import type { Ref } from 'vue'
import type { IPageRes } from '@/api/types/shop'
import { computed, reactive, ref } from 'vue'

/**
 * 「一块列表」的公共形状：已捞到第几页、总页数、行、加载中、错在哪，续页和整块重拉都在这儿。
 *
 * 个人信息那五个 tab、我的订单、收藏、足迹都是同一条节奏（触底续一页、换筛选回第一页整块重拉、
 * 空数据也算加载过 —— 判据是 page===0 而不是 rows.length，否则每次切回来都重捞一遍）。
 * 抽出来一份，页面只管「捞哪一页」和「画成什么样」。
 *
 * 回的是 reactive 而不是散装 ref：一块列表有六格 + 两颗方法，铺在一页上要解构二十个名字，
 * 而且 reactive 的属性会自动解包 —— 模板里 `coupons.rows` 直接就是数组。
 * 一页只放一趟请求：下拉刷新和触底可能同一时刻都进来，loading 没落下就返回。
 */
export function usePagedList<T>(
  fetch: (pageNum: number, pageSize: number) => Promise<IPageRes<T>>,
  pageSize = 10,
) {
  const rows = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const pages = ref(0)
  const page = ref(0)
  const loading = ref(false)
  const error = ref('')

  const hasMore = computed(() => page.value < pages.value)

  async function load() {
    if (loading.value) {
      return
    }
    loading.value = true
    error.value = ''
    try {
      const res = await fetch(page.value + 1, pageSize)
      const records = res.records ?? []
      rows.value = page.value === 0 ? records : [...rows.value, ...records]
      page.value += 1
      total.value = Number(res.total ?? 0)
      pages.value = Number(res.pages ?? 0)
    }
    catch (err) {
      // http 层已经 toast，这一格留着让那块内容换成「加载失败 + 重试」，不把别块一起撤走
      error.value = (err as Error).message
    }
    finally {
      loading.value = false
    }
  }

  /** 换筛选 / 下拉刷新：清回首一页整块重拉（旧那一串上接新筛选的结果会混档） */
  async function reload() {
    rows.value = []
    page.value = 0
    total.value = 0
    pages.value = 0
    await load()
  }

  return reactive({ rows, total, pages, page, loading, error, hasMore, load, reload })
}
