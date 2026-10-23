import type { IAreaOption } from '@/composables/useAreaPicker'
import { areaTree } from '@/composables/useAreaPicker'

/** 智能识别认出来的四格：认不出的回空，由页面决定那一格留不留原来的值 */
export interface IParsedAddress {
  receiverName: string
  receiverPhone: string
  areaCodes: string[]
  detail: string
}

/** 地名在文本里命中的那一段，start/end 用来把这段从详细地址里删掉 */
interface IAreaHit {
  option: IAreaOption
  start: number
  end: number
}

/**
 * 粘一整条地址（「张三 13800001111 广东省深圳市福田区xx路88号」这种）拆成表单那四格。
 *
 * 全程在前端跑、用的是级联选择器那同一棵区划树，所以认出来的代码和手动选出来的一模一样。
 * 顺序是先摘电话、再摘「收货人：」这种带帽子的姓名、再按省→市→区抠地区并把那一段剪掉，
 * 剩下的才是详细地址 —— 反过来先切地址会被电话里的数字带跑。
 * 认不出的一律留空，不猜：宁可让他自己选一下地区，也不能把「科苑路」当成区填进级联里。
 */

/**
 * 电话。前面那一格只能吃空白、标点和「手机/电话」这类帽子，不能吃任意字符 ——
 * 写成 [^\d] 会把「张三 138xxx」的「三」当分隔符一起剪掉（试过一次，姓名就少一个字）。
 */
const PHONE_RE = /(?:^|[\s,，、;；:：/()（）]|手机|电话|联系方式|联系电话|号码)(1[3-9]\d{9})(?!\d)/
const NAME_LABEL_RE = /(?:收货人|收件人|联系人|姓名|名字)[:：\s]*([\u4E00-\u9FA5·]{2,8})/
const SEPARATORS = /[\s,，、;；:：|/]+/
/** 剥掉行政后缀就是「不带单位」的写法：粘过来的常常是「广东深圳南山」；长的排前面，「内蒙古」不能被「省市区」那档抢掉 */
const ADMIN_SUFFIX = /(?:维吾尔自治区|壮族自治区|回族自治区|特别行政区|自治州|自治区|地区|街道|[省市县旗镇区盟])$/
/** 带这些字的是地址片段，不能当收货人 */
const NOT_A_NAME = /[省市区县路街道号村苑园站店庄桥巷弄]/
/** 帽子词（冒号一当分隔符就被切成单独一颗）和剪剩的半个帽子，留在详细地址里没有意义 */
const FILLER = /^(?:收货人?|收件人|联系人|姓名|地址|详细地址|所在地区|地区|省市区|邮编|邮政编码|备注|标签|谢谢|送达|[手机收]|手机号|手机|电话|号码|联系电话|联系方式)$/

function nameKeys(label: string): string[] {
  const short = label.replace(ADMIN_SUFFIX, '')
  return short.length >= 2 && short !== label ? [label, short] : [label]
}

/** 在这一级里找最早出现的地名；同一位置命中全称和短名时取长的（「深圳市」赢「深圳」） */
function findArea(text: string, from: number, list: IAreaOption[]): IAreaHit | undefined {
  let best: IAreaHit | undefined
  for (const option of list) {
    for (const key of nameKeys(option.label)) {
      const start = text.indexOf(key, from)
      if (start < 0) {
        continue
      }
      // 全称命中就不必再试短名，否则「深圳」会先把「深圳市」那一段截短
      const end = start + key.length
      if (!best || start < best.start || (start === best.start && end > best.end)) {
        best = { option, start, end }
      }
      break
    }
  }
  return best
}

/** 从文本里抠出省市区三级，连它们在文本里占的那一段一起回 */
function matchArea(text: string): { codes: string[], start: number, end: number } | undefined {
  const provinces = areaTree()
  const province = findArea(text, 0, provinces)
  if (province) {
    const codes = [province.option.value]
    let end = province.end
    const city = findArea(text, end, province.option.children ?? [])
    if (city) {
      codes.push(city.option.value)
      end = city.end
      const district = findArea(text, end, city.option.children ?? [])
      if (district) {
        codes.push(district.option.value)
        end = district.end
      }
    }
    else {
      // 少写一级：直辖市那一级和省同名（「北京市朝阳区」里再找市是找不到的），拿区反查它的市
      let adopted: { city: IAreaOption, district: IAreaHit } | undefined
      for (const one of province.option.children ?? []) {
        const district = findArea(text, end, one.children ?? [])
        if (district && (!adopted || district.start < adopted.district.start)) {
          adopted = { city: one, district }
        }
      }
      if (adopted) {
        codes.push(adopted.city.value, adopted.district.option.value)
        end = adopted.district.end
      }
    }
    return { codes, start: province.start, end }
  }

  // 连省都没写（「深圳市南山区科技园15号」）：从市这一级接住，区照样接着找
  let hit: { province: IAreaOption, city: IAreaHit } | undefined
  for (const one of provinces) {
    const city = findArea(text, 0, one.children ?? [])
    if (city && (!hit || city.start < hit.city.start)) {
      hit = { province: one, city }
    }
  }
  if (!hit) {
    return undefined
  }
  const codes = [hit.province.value, hit.city.option.value]
  let end = hit.city.end
  const district = findArea(text, end, hit.city.option.children ?? [])
  if (district) {
    codes.push(district.option.value)
    end = district.end
  }
  return { codes, start: hit.city.start, end }
}

/** 把已经吃掉的那一段摘掉，留个空格免得前后两截粘成「李四广东省」 */
function cut(text: string, from: number, to: number) {
  return `${text.slice(0, from)} ${text.slice(to)}`
}

/** 从这一串词里从后往前挑一个像姓名的（挨着地区那颗、或整条最后那颗最像），挑中就从串里摘掉 */
function takeName(tokens: string[]): string {
  for (let i = tokens.length - 1; i >= 0; i--) {
    const one = tokens[i].replace(/(?:收货|收件|收|先生|女士|小姐|电话|手机|联系方式)$/, '')
    if (/^[\u4E00-\u9FA5·]{2,6}$/.test(one) && !NOT_A_NAME.test(one)) {
      tokens.splice(i, 1)
      return one
    }
  }
  return ''
}

export function parseAddressText(raw: string): IParsedAddress {
  const out: IParsedAddress = { receiverName: '', receiverPhone: '', areaCodes: [], detail: '' }
  let text = (raw ?? '').replace(/\s+/g, ' ').trim()
  if (!text) {
    return out
  }

  const phone = PHONE_RE.exec(text)
  if (phone) {
    out.receiverPhone = phone[1]
    text = cut(text, phone.index, phone.index + phone[0].length)
  }

  const nameLabel = NAME_LABEL_RE.exec(text)
  if (nameLabel) {
    out.receiverName = nameLabel[1]
    text = cut(text, nameLabel.index, nameLabel.index + nameLabel[0].length)
  }

  const area = matchArea(text)
  let before = text
  let after = ''
  if (area) {
    out.areaCodes = area.codes
    before = text.slice(0, area.start)
    after = text.slice(area.end)
  }

  const head = before.split(SEPARATORS).filter(one => one && !FILLER.test(one))
  const tail = after.split(SEPARATORS).filter(one => one && !FILLER.test(one))
  if (!out.receiverName) {
    // 姓名两种摆法都常见：「张三 138xxx 广东省…」摆在前头，「广东省… 张三 138xxx」摆在尾巴。
    // 对不上就留空，不拿地址片段凑
    out.receiverName = takeName(head) || takeName(tail)
  }

  out.detail = [...head, ...tail].join(' ')

  return out
}
