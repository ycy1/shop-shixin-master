import { useCascaderAreaData } from '@vant/area-data'

/**
 * 省市区三列选择器的数据源（地址编辑、修改资料两页共用）。
 *
 * 数据用 @vant/area-data，和后台收货地址那页、Web 前台那份 AddressForm 是同一份，
 * 所以三处选出来的行政区划代码、拼出来的中文一模一样 —— area_code 是「省,市,区」逗号分隔，
 * area_zh 是用空格拼的那串中文，两列一起送（项目口径：地区只存这两列）。
 *
 * wd-col-picker 要的是「每列一个数组」的 columns，并在每次滚某一列时由 columnChange 把下一列换掉；
 * 这里一次把三级都备好了（数据是静态的，不用分批捞），columnChange 只是把选中那颗的 children 递回去。
 */

/** 喂给 wd-col-picker 的一格：value 是区划代码，label 是中文，children 留着给下一列 */
export interface IAreaOption {
  value: string
  label: string
  children?: IAreaOption[]
}

/** wd-col-picker 的 column-change 回调参数（wot 那份类型没导出，这里按组件实现收） */
export interface IAreaColumnChange {
  selectedItem: IAreaOption
  index: number
  resolve: (nextColumn: IAreaOption[]) => void
  finish: (isOk?: boolean) => void
}

/** 收货地址那一格「标签」的候选（库上是字典 address_tag，值就是这几个中文，口径同 shop-web） */
export const ADDRESS_TAGS = ['家', '公司', '学校']

/** 那份包的回参形状（它自己的 CascaderOption 没导出，就从函数返回类型上取） */
type IVantNode = ReturnType<typeof useCascaderAreaData>[number]

/** 这份数据要遍历三万次比较，模块级转一次就够（同 shop-admin 那份 composable 的取法） */
let tree: IAreaOption[] | null = null

function toOptions(list: IVantNode[]): IAreaOption[] {
  return list.map(one => ({
    value: one.value,
    label: one.text,
    children: one.children?.length ? toOptions(one.children) : undefined,
  }))
}

/** 省那一级（顺带把整棵树转出来缓存住）；地址智能识别也要用这一棵，所以导出去 */
export function areaTree(): IAreaOption[] {
  if (!tree) {
    tree = toOptions(useCascaderAreaData())
  }
  return tree
}

/** 库里的 area_code（'110000,110100,110101'）拆回三级代码；只有两级的市（东莞、中山）就拆出两颗 */
export function parseAreaCode(areaCode?: string | null): string[] {
  return (areaCode ?? '').split(',').map(one => one.trim()).filter(Boolean)
}

/** 按代码串往下找，回它拼出来的中文（空格分隔）—— 找不到就回已经命中的那几级，不抛 */
export function areaZhOf(codes: string[]): string {
  let list = areaTree()
  const names: string[] = []
  for (const code of codes) {
    const hit = list.find(one => one.value === code)
    if (!hit) {
      break
    }
    names.push(hit.label)
    list = hit.children ?? []
  }
  return names.join(' ')
}

/**
 * 给 wd-col-picker 的 columns：按已选层数给几列（没选就给省那一列，选到市就给两列），
 * 每列都按已选那颗铺好它的下一级，所以回显时打开停在要接着选的那一列上。
 *
 * 两条都是组件实现里写死的，踩了就是「翻不动」或「点开一张白列」：
 * 1. 它按 columns 排那几列，只给一列就只渲染一列；
 * 2. columns 一变就把指针挪到最后一列（currentCol = length - 1），末列给空数组等于让人对着白列选。
 * 不建区的市（东莞、中山）到市这一层就完事，所以那种市只给两列；滚到哪一级由 columnChange 接着补下一列。
 */
export function areaColumns(codes: string[]): IAreaOption[][] {
  const list = areaTree()
  const province = codes[0] ? list.find(one => one.value === codes[0]) : undefined
  if (!province) {
    return [list]
  }
  const cities = province.children ?? []
  const city = codes[1] ? cities.find(one => one.value === codes[1]) : undefined
  if (!city) {
    return [list, cities]
  }
  const districts = city.children ?? []
  // 不建区的市（东莞、中山）只给两列：末列空的话点开停在空白列上
  return districts.length ? [list, cities, districts] : [list, cities]
}

/** 滚到某一级：把它的下一级铺给下一列；没有下一级（区这一层、或不建区的市）就直接收尾 */
export function onAreaColumnChange({ selectedItem, resolve, finish }: IAreaColumnChange) {
  const next = selectedItem.children ?? []
  if (!next.length) {
    finish()
    return
  }
  resolve(next)
}
