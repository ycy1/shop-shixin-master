<template>
  <!--
    签到记录：日历形式，一格 = 归属某一天（不是操作那一刻，所以这个月补的那个签落在被补那天那一格上），
    一次只看一个月，换月就重捞。两处共用这一份：
      后台「签到管理」= 给别人看、给别人补（那颗压 activity:signin:makeup，能补的日子不限）；
      后台「个人信息」= 给自己看、自己补（两颗都走 comm、不压权限，只能补最近 SELF_MAKEUP_DAYS 天）。
    给分口径两边一模一样：读参数管理 points_config 的 signin 那一档，落签到行 + 加积分账 + 记积分流水在同一个事务里。
  -->
  <el-dialog v-model="dialogVisible" :title="title" width="1000px" append-to-body destroy-on-close @open="handleOpen">
    <!-- 汇总那一排摆在日历上面，月份跟着日历走；「今天签到」只有本人模式才有，且放在日历上面那一行 -->
    <div class="record-toolbar">
      <span class="record-summary" title="「最近连续」算的是这个人到最近一次签到为止连着签了几天（从最晚那一天往前回数，最多报到 7 天），跟翻到哪一个月不相干">
        {{ recordMonth }} 签到 {{ monthSignedDays }} 天 / 其中补签 {{ monthMakeupDays }} 天 / 本月获得 {{ monthPoints }} 分 / 最近连续 {{ currentContinuous }} 天
      </span>
      <div class="record-actions">
        <span class="record-tip">{{ tipText }}</span>
        <el-button v-if="self" type="primary" :disabled="todaySigned" :loading="signLoading" @click="handleSign">
          {{ todaySigned ? '今天已签到' : '今天签到' }}
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="record-calendar">
      <el-calendar v-model="calendarDate">
        <template #date-cell="{ data }">
          <!-- 上/下个月的邻月格子只留个日期：捞的是当前看的那一个月，邻月那几格属于另一本账 -->
          <div v-if="data.type !== 'current-month'" class="day-cell is-blank">{{ dayNum(data.day) }}</div>
          <div v-else-if="recordOf(data.day)" class="day-cell is-signed">
            <div class="day-head">
              <span class="day-num">{{ dayNum(data.day) }}</span>
              <el-tag size="small" :type="dict.tag(SIGN_TYPE_DICT, recordOf(data.day).signType)">
                {{ dict.label(SIGN_TYPE_DICT, recordOf(data.day).signType) }}
              </el-tag>
            </div>
            <div class="day-body">
              +{{ countText(recordOf(data.day).points) }} 分
            </div>
          </div>
          <div v-else class="day-cell">
            <span class="day-num">{{ dayNum(data.day) }}</span>
            <!-- 补签的判据就这一句：还没签的那些日子里够得着的那几天。今天和将来不给补（那天还没过完，归「今天签到」） -->
            <el-button v-if="canMakeup(data.day)" v-permission="makeupPermission" type="primary" link class="makeup-btn"
              :loading="makeupLoading" @click="handleMakeup(data.day)">补签</el-button>
          </div>
        </template>
      </el-calendar>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listShopSigninRecordApi,
  makeupSelfShopSigninApi,
  makeupShopSigninApi,
  signShopSigninApi
} from '@/api/activity/signin'
import { useDict } from '@/utils/dictCache'

/** 签到类型是枚举列，标签一律走字典，不在这份组件里写死中文名 */
const SIGN_TYPE_DICT = 'sign_type'

/**
 * 本人补签的窗口：只允许补今天往前数这些天内的日子。
 * 后台那颗（给别人补）不限 —— 那是操作员替用户兜底的动作，有人工判断兜着；
 * 自己这一头不封顶就能把历史空日子全补一遍刷积分，所以服务端 makeupSelf 里钉了同一个数，
 * 这里只是把够不着的那些日子画成不能点，改数要两处一起改。
 */
const SELF_MAKEUP_DAYS = 7

const props = withDefaults(defineProps<{
  visible: boolean
  /** 看谁的签到：后台模式就是列表点进来那个人；本人模式传登录者自己的 id（服务端还会按账号类型再钉一次） */
  userId?: number | string
  title?: string
  /** 汇总/确认文案里那个人叫什么（后台模式用昵称，本人模式没有这格） */
  userText?: string
  /** 本人模式：日历上面多一颗「今天签到」，补签调 comm 那条、并受 SELF_MAKEUP_DAYS 窗口限制 */
  self?: boolean
}>(), {
  userId: undefined,
  title: '签到记录',
  userText: '',
  self: false
})

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  /** 签到或补签成功：带回来的就是刚落下的那一行（调用方用它刷自己的列表/积分那一块） */
  changed: [record: any]
}>()

const dict = useDict(SIGN_TYPE_DICT)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const loading = ref(false)
/** 日历绑的是"看哪一个月"，换月（上一月/下一月/今天，或直接点某格）都会更新它，所以月份跟着它走 */
const calendarDate = ref<Date>(new Date())
/** 这个月签过的那几天，按归属日（yyyy-MM-dd）索引；没签的日子在这张表里根本没有行，由日历画成可补签的空格 */
const recordMap = ref<Record<string, any>>({})
/** 已经捞回来的那一个月：换月才重捞，点开弹窗那一次和日历自己刷同一个月时不重复问服务端 */
const loadedMonth = ref('')
const signLoading = ref(false)
const makeupLoading = ref(false)
/** 今天那一串（yyyy-MM-dd）：补签的判据是"比这一天早"，用串比是因为 ISO 日期串按字典序就是按时间序 */
const todayText = ref(dayjs().format('YYYY-MM-DD'))
const todayMonth = ref(dayjs().format('YYYY-MM'))
/** 今天往前数 SELF_MAKEUP_DAYS 天那一条（yyyy-MM-dd）：本人模式只补得到它之后的那些日子 */
const earliestMakeup = ref(dayjs().subtract(SELF_MAKEUP_DAYS, 'day').format('YYYY-MM-DD'))
/**
 * 今天签过没有：只在"看的就是当月"那一次捞回来时定，切去别的月不跟着清
 *（否则翻到上个月会变成"今天还没签"，而服务端那一行早就在了）
 */
const todaySigned = ref(false)
/**
 * 最近连续几天：这张表上不存这一列，服务端从这个人最晚的那一天往前回数（最多 7 天）现算，
 * 并把同一个数盖在返回的每一行上，所以读第一行就够了。
 * 它跟"看的是哪一个月"不相干，因此翻到一个空月份时不跟着清成 0（那个月没行，数还是那一个数）。
 */
const currentContinuous = ref(0)

/** 这一个月的汇总（读的就是 recordMap 那几天，日历换月就跟着变） */
const recordMonth = computed(() => dayjs(calendarDate.value).format('YYYY-MM'))
const monthRecords = computed(() => Object.values(recordMap.value))
const monthSignedDays = computed(() => monthRecords.value.length)
const monthMakeupDays = computed(() => monthRecords.value.filter((item: any) => Number(item.signType) === 2).length)
const monthPoints = computed(() => monthRecords.value.reduce((sum: number, item: any) => sum + Number(item.points || 0), 0))

const tipText = computed(() => props.self
  ? `点最近 ${SELF_MAKEUP_DAYS} 天内没签的过去日子即可补签，补签给的分和正常签到一样`
  : '点没签的过去日子即可补签，补签给的分和正常签到一样（读参数管理 points_config 的 signin 那一档）')

/** 本人模式那两条走 comm，只验登录态；后台那颗压真权限。空数组 = v-permission 不拦 */
const makeupPermission = computed(() => (props.self ? [] : ['activity:signin:makeup']))

/** 确认文案里那个人：后台模式点名是谁，本人模式就是说"自己" */
const targetText = computed(() => (props.self ? '' : `给 ${props.userText || `用户 #${props.userId}`} `))

/** 天数、积分都是整数，脏数据读到的空格子当 0 说，别让日历里跳出一片空白 */
const countText = (value: any) => {
  const count = Number(value)
  return Number.isFinite(count) ? String(count) : '0'
}

/** 日历那一格上的日号（数据给的是 yyyy-MM-dd，格子里只说"几号"，月头由日历顶上的标题说） */
const dayNum = (day: string) => Number(day.slice(8, 10))

const recordOf = (day: string) => recordMap.value[day]

/** 能不能补：比今天早的（今天和将来归「今天签到」那颗，服务端也会拒），本人模式再受那 7 天窗口挡一道 */
const canMakeup = (day: string) => day < todayText.value && (!props.self || day >= earliestMakeup.value)

/** 某个人的某一个月：只回签过的那几天（一个月最多 31 行，所以不分页） */
const getRecordList = async () => {
  loading.value = true
  const month = recordMonth.value
  try {
    const { data } = await listShopSigninRecordApi({ userId: props.userId, month })
    const map: Record<string, any> = {}
    ;(data || []).forEach((item: any) => {
      map[dayjs(item.signDate).format('YYYY-MM-DD')] = item
    })
    recordMap.value = map
    loadedMonth.value = month
    // 那一个数与月无关（服务端盖在返回的每一行上，读第一行就够了），所以翻到一个没记录的月份时不清零
    if ((data || []).length > 0) currentContinuous.value = Number(data[0]?.currentContinuous ?? 0)
    if (month === todayMonth.value) todaySigned.value = !!map[todayText.value]
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 每次打开都从头来：回到当月、重新问服务端（签到状态随时在变，缓存一个月就是旧样子） */
const handleOpen = () => {
  todayText.value = dayjs().format('YYYY-MM-DD')
  todayMonth.value = dayjs().format('YYYY-MM')
  earliestMakeup.value = dayjs().subtract(SELF_MAKEUP_DAYS, 'day').format('YYYY-MM-DD')
  recordMap.value = {}
  loadedMonth.value = ''
  todaySigned.value = false
  currentContinuous.value = 0
  calendarDate.value = new Date()
  getRecordList()
}

/** 今天签到（本人模式才有）：没有表单（签的就是今天这一格、给谁由登录态定），所以直接点，服务端挡"今天已经签过了" */
const handleSign = async () => {
  signLoading.value = true
  try {
    const { data } = await signShopSigninApi()
    ElMessage.success(`签到成功：连续第 ${countText(data?.currentContinuous)} 天，获得 ${countText(data?.points)} 分`)
    todaySigned.value = true
    await getRecordList()
    emit('changed', data)
  } catch (error) {
  } finally {
    signLoading.value = false
  }
}

/**
 * 补签：日历上点一格没签的过去日子。表单没有东西可填（给谁、补哪天这两格点了那一格就说清了），
 * 所以只弹一句确认；积分、签到类型、连续天数一概由服务端算，这一头连数量都不传
 */
const handleMakeup = (day: string) => {
  ElMessageBox.confirm(
    `确认${targetText.value}补签 ${day} 吗？补下来的这一行和正常签到一样记分。`,
    '签到补签',
    { type: 'warning', confirmButtonText: '确 定', cancelButtonText: '取 消' }
  ).then(async () => {
    makeupLoading.value = true
    try {
      // 两条端点是同一套判据的两个头：本人那条只收补哪天，userId 由服务端钉登录 id
      const { data } = props.self
        ? await makeupSelfShopSigninApi({ signDate: day })
        : await makeupShopSigninApi({ userId: props.userId, signDate: day })
      ElMessage.success(`已补签 ${day}，获得 ${countText(data?.points)} 分`)
      await getRecordList()
      emit('changed', data)
    } catch (error) {
    } finally {
      makeupLoading.value = false
    }
  }).catch(() => { })
}

/** 换月就重捞：日历顶上那三颗（上一月/下一月/今天）和点任意一格都会更新绑着的那一天，同一个月不重复捞 */
watch(calendarDate, () => {
  if (props.visible && recordMonth.value !== loadedMonth.value) {
    getRecordList()
  }
})
</script>

<style scoped lang="scss">
/* 日历上面那一排：这个月那三个数在左、提示和「今天签到」在右 */
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .record-summary {
    color: var(--el-text-color-secondary);
  }

  .record-actions {
    display: flex;
    align-items: center;
    gap: 12px;

    .record-tip {
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }
  }
}

/* 日历那一格：默认内容被 date-cell 换掉了，所以格子得自己撑出高度、让日期贴在左上 */
.record-calendar {
  min-height: 620px;

  :deep(.el-calendar-day) {
    height: 92px;
    padding: 4px 6px;
    overflow: hidden;
  }

  .day-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
    height: 100%;
    font-size: 12px;

    .day-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .day-body {
      color: var(--el-text-color-secondary);
    }

    /* 邻月和没签的那些格子只留日号，别让人以为它们也是数据 */
    &.is-blank {
      color: var(--el-text-color-placeholder);
    }

    &.is-signed {
      color: var(--el-color-success);
    }
  }

  /* 补签那一颗贴在格子下面：一格就一个动作，不做二次弹窗表单 */
  .makeup-btn {
    margin-top: auto;
    padding: 0;
    height: auto;
  }
}
</style>
