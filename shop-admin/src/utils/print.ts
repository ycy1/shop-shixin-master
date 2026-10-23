import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { dictLabel } from '@/utils/dictCache'
import printDocumentHtml from '@/templates/print.html?raw'
import printStyle from '@/templates/print.css?raw'
// 单据模板按业务分目录，和 views/ 下的那层命名对得上：erp = 库存管理那几张单、store = 门店/招商
import receiptHtml from '@/templates/erp/receipt.html?raw'
import receiptItemHtml from '@/templates/erp/receipt-item.html?raw'
import outboundHtml from '@/templates/erp/outbound.html?raw'
import outboundItemHtml from '@/templates/erp/outbound-item.html?raw'
import investmentHtml from '@/templates/store/investment.html?raw'

/**
 * 公共打印：往新窗口写一份打印用的 HTML，交给浏览器自己的打印窗口，
 * 在那里选「目标=另存为 PDF」就能拿到 PDF，不引任何打印/图表依赖。
 *
 * 版式全在 src/templates/ 里：根上的 print.html（外壳）和 print.css（公共样式）不属于某个业务，
 * 各单据模板按业务分子目录（erp/、store/）。这里只做「转义 + 填占位 + 开窗打印」。
 * 新单据要打印：在自己那个业务目录下加一份模板 + 一个 xxxSheet 填模板的函数，最后都走 printDocument()。
 */

const DICT_RECEIPT_STATUS = 'purchase_in_status'
const DICT_RECEIPT_SOURCE = 'inbound_source'
const DICT_OUTBOUND_STATUS = 'sale_outbound_status'
const DICT_OUTBOUND_SOURCE = 'sale_outbound_source'
const DICT_INVEST_INTENT = 'invest_intent_type'
const DICT_INVEST_SOURCE = 'invest_source'
const DICT_INVEST_STATUS = 'invest_status'

/** 进 HTML 前一律转义：商品名、备注这类都是用户手填的文本 */
export const escapeHtml = (val: any) =>
  String(val ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

/** 把模板里的 {{key}} 换成 data[key]（值需调用方自己转义好） */
const fill = (tpl: string, data: Record<string, string>) =>
  tpl.replace(/\{\{(\w+)\}\}/g, (_match, key: string) => data[key] ?? '')

const num = (val: any) => Number(val || 0)

const numText = (val: any) => String(num(val))

/** 还剩多少没做 = 计划 − 实际（数量可以现减，钱不行） */
const remaining = (plan: any, done: any) => numText(Math.max(0, num(plan) - num(done)))

/** 金额口径同各列表页（后端返回的 decimal 是字符串） */
const priceText = (price: any) => (price == null || price === '' ? '￥0.00' : `￥${Number(price).toFixed(2)}`)

const day = (time: any) => {
  if (!time) return '-'
  const d = dayjs(time)
  return d.isValid() ? d.format('YYYY-MM-DD') : '-'
}

const nameOrDeleted = (name: any, id: any, label: string) =>
  name || (id ? `已删除${label} #${id}` : '-')

/** 明细一行都没有时占位，列数得跟各自模板的表头对上 */
const emptyItemRows = (cols: number) =>
  `<tr><td colspan="${cols}" class="c empty">这张单没有明细</td></tr>`

/**
 * 打开打印窗口，写入 bodyHtml 并唤起打印。
 * @param title 窗口标题，也是「另存为 PDF」时浏览器建议的文件名
 * @param bodyHtml 已经拼好的正文（若干 .sheet，见 templates/print.css）
 */
export const printDocument = (title: string, bodyHtml: string) => {
  // 弹窗被浏览器拦下来就没地方写，直接说清楚要放行，别静默
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.error('打印窗口被浏览器拦截了，请允许本页弹出窗口后重试')
    return
  }
  win.document.write(fill(printDocumentHtml, { title: escapeHtml(title), style: printStyle, body: bodyHtml }))
  win.document.close()
  // 等这一帧渲染完再唤起打印，否则部分浏览器会打出空白页；打错了外壳上那两颗按钮还能手动再来
  setTimeout(() => {
    win.focus()
    win.print()
  }, 300)
}

/** 明细行的规格那一格：一行是一个具体规格，没这一格同一商品的几行在纸上分不出来 */
const specText = (item: any) => item.specText || '默认规格'

/** 入库单明细的一行 */
const receiptItemRow = (item: any, index: number) =>
  fill(receiptItemHtml, {
    index: String(index + 1),
    productName: escapeHtml(item.productName || `已删除规格 #${item.skuId}`),
    specText: escapeHtml(specText(item)),
    productCode: escapeHtml(item.productCode),
    quantity: numText(item.quantity),
    receivedQuantity: numText(item.receivedQuantity),
    remaining: remaining(item.quantity, item.receivedQuantity),
    price: escapeHtml(priceText(item.price)),
    amount: escapeHtml(priceText(item.amount))
  })

/** 一张入库单（= 一页）。钱一律读库里那几个数，不在这里现乘现加 */
const receiptSheet = (row: any) => {
  const items = row.items || []
  return fill(receiptHtml, {
    receiptNo: escapeHtml(row.receiptNo || '-'),
    // 来源单据：2 采购入库挂采购单、1 线上退货挂退款单，两列只会有一列有值（同两页的 sourceNoOf）
    sourceNo: escapeHtml(row.purchaseNo || row.refundNo || '未关联'),
    inboundSource: escapeHtml(dictLabel(DICT_RECEIPT_SOURCE, row.inboundSource)),
    status: escapeHtml(dictLabel(DICT_RECEIPT_STATUS, row.status)),
    supplierName: escapeHtml(nameOrDeleted(row.supplierName, row.supplierId, '供应商')),
    warehouseName: escapeHtml(nameOrDeleted(row.warehouseName, row.warehouseId, '仓库')),
    userName: escapeHtml(row.userName || '未记录'),
    receiveTime: escapeHtml(day(row.receiveTime)),
    totalQuantity: numText(row.totalQuantity),
    totalReceived: numText(row.totalReceived),
    pendingQuantity: remaining(row.totalQuantity, row.totalReceived),
    totalAmount: escapeHtml(priceText(row.totalAmount)),
    createTime: escapeHtml(day(row.createTime)),
    remark: escapeHtml(row.remark || '-'),
    itemRows: items.length ? items.map(receiptItemRow).join('') : emptyItemRows(9)
  })
}

/**
 * 打印入库单（含明细）。数据就是弹窗里已经捞到的那几行 ShopErpPurchaseIn + items，不另开接口。
 * @param receipts 一张或多张入库单
 * @param docTitle 不传时：单张用「入库单 + 单号」，多张用「入库单 N 张」
 */
export const printReceipts = (receipts: any[], docTitle?: string) => {
  const list = (receipts || []).filter(Boolean)
  if (list.length === 0) {
    ElMessage.warning('没有可打印的入库单')
    return
  }
  const title = docTitle || (list.length === 1 ? `入库单 ${list[0].receiptNo || ''}`.trim() : `入库单 ${list.length} 张`)
  printDocument(title, list.map(receiptSheet).join(''))
}

/** 出库单明细的一行。出库仓库在行上不在主表：一张单的几行可能从几家仓分别发 */
const outboundItemRow = (item: any, index: number) =>
  fill(outboundItemHtml, {
    index: String(index + 1),
    productName: escapeHtml(item.productName || `已删除规格 #${item.skuId}`),
    specText: escapeHtml(specText(item)),
    productCode: escapeHtml(item.productCode),
    warehouseName: escapeHtml(nameOrDeleted(item.warehouseName, item.warehouseId, '仓库')),
    quantity: numText(item.quantity),
    outboundQuantity: numText(item.outboundQuantity),
    remaining: remaining(item.quantity, item.outboundQuantity),
    price: escapeHtml(priceText(item.costPrice)),
    amount: escapeHtml(priceText(item.amount))
  })

/** 一张出库单（= 一页）。同样不在这里现算钱 */
const outboundSheet = (row: any) => {
  const items = row.items || []
  return fill(outboundHtml, {
    outboundNo: escapeHtml(row.outboundNo || '-'),
    // 来源单据：order_no 按 outbound_source 分指订单/销售单/采购单，库里存的就是那张单的号
    sourceNo: escapeHtml(row.orderNo || '未关联'),
    outboundSource: escapeHtml(dictLabel(DICT_OUTBOUND_SOURCE, row.outboundSource)),
    status: escapeHtml(dictLabel(DICT_OUTBOUND_STATUS, row.status)),
    storeName: escapeHtml(nameOrDeleted(row.storeName, row.storeId, '门店')),
    userName: escapeHtml(row.userName || '未记录'),
    outboundTime: escapeHtml(day(row.outboundTime)),
    totalQuantity: numText(row.totalQuantity),
    totalOut: numText(row.totalOut),
    pendingQuantity: remaining(row.totalQuantity, row.totalOut),
    totalAmount: escapeHtml(priceText(row.totalAmount)),
    createTime: escapeHtml(day(row.createTime)),
    remark: escapeHtml(row.remark || '-'),
    itemRows: items.length ? items.map(outboundItemRow).join('') : emptyItemRows(10)
  })
}

/**
 * 打印出库单（含明细）。数据同样是弹窗里已经捞到的那几行 ShopErpSaleOutbound + items。
 * @param outbounds 一张或多张出库单
 * @param docTitle 不传时：单张用「出库单 + 单号」，多张用「出库单 N 张」
 */
export const printOutbounds = (outbounds: any[], docTitle?: string) => {
  const list = (outbounds || []).filter(Boolean)
  if (list.length === 0) {
    ElMessage.warning('没有可打印的出库单')
    return
  }
  const title = docTitle || (list.length === 1 ? `出库单 ${list[0].outboundNo || ''}`.trim() : `出库单 ${list.length} 张`)
  printDocument(title, list.map(outboundSheet).join(''))
}

/** 一条招商线索（= 一页）。登记门店这一格由打印入口塞进行对象里，不是本表列 */
const investmentSheet = (row: any) =>
  fill(investmentHtml, {
    investmentNo: escapeHtml(row.investmentNo || '-'),
    storeName: escapeHtml(row.storeName || '-'),
    applicantName: escapeHtml(row.applicantName || '-'),
    applicantPhone: escapeHtml(row.applicantPhone || '-'),
    userName: escapeHtml(row.userName || '未分配'),
    status: escapeHtml(dictLabel(DICT_INVEST_STATUS, row.status)),
    intentType: escapeHtml(dictLabel(DICT_INVEST_INTENT, row.intentType)),
    source: escapeHtml(dictLabel(DICT_INVEST_SOURCE, row.source)),
    budgetAmount: escapeHtml(priceText(row.budgetAmount)),
    hasExperience: row.hasExperience === 1 ? '有' : '无',
    signTime: escapeHtml(day(row.signTime)),
    expiryTime: escapeHtml(day(row.expiryTime)),
    area: escapeHtml([row.areaZh, row.address].filter(Boolean).join(' ') || '-'),
    contractFile: escapeHtml(row.contractFile || '未上传'),
    remark: escapeHtml(row.remark || '-'),
    createTime: escapeHtml(day(row.createTime)),
    printTime: dayjs().format('YYYY-MM-DD HH:mm')
  })

/**
 * 打印招商线索登记单。数据同样是弹窗里已经捞好的那一份，不另开接口。
 * @param investments 一条或多条招商线索
 * @param docTitle 不传时：单条用「招商信息 + 编号」，多条用「招商信息 N 条」
 */
export const printInvestments = (investments: any[], docTitle?: string) => {
  const list = (investments || []).filter(Boolean)
  if (list.length === 0) {
    ElMessage.warning('没有可打印的招商信息')
    return
  }
  const title = docTitle || (list.length === 1 ? `招商信息 ${list[0].investmentNo || ''}`.trim() : `招商信息 ${list.length} 条`)
  printDocument(title, list.map(investmentSheet).join(''))
}
