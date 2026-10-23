<template>
  <div class="app-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span class="title">我的送货员信息</span>
          <div class="header-actions">
            <!-- 默认是查看态，只有这一份档案能改，所以按钮不给权限指令 -->
            <el-button v-if="!editing" type="primary" icon="Edit" :disabled="!info.id" @click="handleEdit">编辑</el-button>
            <template v-else>
              <el-button @click="handleCancel">取消</el-button>
              <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
            </template>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && !info.id" description="当前账号还没有送货员档案，请联系管理员" />

      <template v-else>
        <!-- 基本资料 + 证件信息 + 接单状态可编辑（和服务端 updateMyInfo 的白名单一一对应），其余只读 -->
        <template v-if="!editing">
          <el-descriptions title="基本资料" :column="2" border>
            <el-descriptions-item label="头像">
              <el-image v-if="info.avatar" :src="info.avatar" :preview-src-list="[info.avatar]" preview-teleported
                fit="contain" style="width: 60px; height: 60px; border-radius: 4px" />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="送货员姓名">{{ info.riderName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ info.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="车牌号">{{ info.vehicleNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务城市">{{ info.serviceCity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务区域">{{ info.serviceArea || '-' }}</el-descriptions-item>
            <!-- 常驻地点的经纬度决定他那份「我的配送单」离得近的排前面，地址文本只是给人看的备注 -->
            <el-descriptions-item label="常驻地点">{{ info.residentAddress || '未填写' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions class="mt-16" title="证件信息" :column="2" border>
            <el-descriptions-item label="身份证号">{{ info.idCard || '-' }}</el-descriptions-item>
            <el-descriptions-item label="健康证到期日">{{ validate.formatTime(info.healthCertExpire, 'YYYY-MM-DD') }}</el-descriptions-item>
            <el-descriptions-item label="身份证正面">
              <el-image v-if="info.idCardFront" :src="info.idCardFront" :preview-src-list="certImages"
                :initial-index="certIndex(info.idCardFront)" preview-teleported fit="cover" class="cert-thumb" />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="身份证反面">
              <el-image v-if="info.idCardBack" :src="info.idCardBack" :preview-src-list="certImages"
                :initial-index="certIndex(info.idCardBack)" preview-teleported fit="cover" class="cert-thumb" />
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="健康证">
              <el-image v-if="info.healthCert" :src="info.healthCert" :preview-src-list="certImages"
                :initial-index="certIndex(info.healthCert)" preview-teleported fit="cover" class="cert-thumb" />
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="130px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="头像" prop="avatar">
                <upload-image v-model="form.avatar" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="送货员姓名" prop="riderName">
                <el-input v-model="form.riderName" placeholder="请输入姓名" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="车牌号" prop="vehicleNo">
                <el-input v-model="form.vehicleNo" placeholder="选填" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务城市" prop="serviceCity">
                <el-input v-model="form.serviceCity" placeholder="选填，如 杭州市" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务区域" prop="serviceArea">
                <el-input v-model="form.serviceArea" placeholder="多个区域用逗号分隔" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="常驻地点" prop="residentAddress">
                <el-input v-model="form.residentAddress" placeholder="请输入常驻地点，或右侧地图选点" maxlength="255" />
              </el-form-item>
            </el-col>
            <el-col :span="18">
              <!-- 这两格要一起填（后端 validate 会挡只填一半），都不填也没关系 —— 只是他的配送列表排不出「离我最近」 -->
              <el-form-item label="常驻经纬度" prop="residentLng">
                <div class="pair-field">
                  <el-input-number v-model="form.residentLng" :precision="6" :controls="false" placeholder="经度"
                    style="width: 100%" />
                  <span class="pair-split">-</span>
                  <el-input-number v-model="form.residentLat" :precision="6" :controls="false" placeholder="纬度"
                    style="width: 100%" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <!-- 整表 label-width 是 130px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
              <el-form-item label-width="0">
                <shop-map :longitude="form.residentLng" :latitude="form.residentLat" title="选择常驻位置"
                  @confirm="handleResidentLocation" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">证件信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="身份证号" prop="idCard">
                <el-input v-model="form.idCard" placeholder="选填" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="健康证到期日" prop="healthCertExpire">
                <el-date-picker v-model="form.healthCertExpire" type="datetime" placeholder="选填"
                  value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证正面" prop="idCardFront">
                <upload-image v-model="form.idCardFront" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证反面" prop="idCardBack">
                <upload-image v-model="form.idCardBack" :size="120" source="rider" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="健康证图片" prop="healthCert">
                <upload-image v-model="form.healthCert" :size="120" source="rider" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">接单状态</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="交通工具" prop="vehicleType">
                <el-select v-model="form.vehicleType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_vehicle_type')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="在线状态" prop="onlineStatus">
                <el-select v-model="form.onlineStatus" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_online_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="工作状态" prop="workStatus">
                <el-select v-model="form.workStatus" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('rider_work_status')" :key="item.value" :label="item.label"
                    :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-descriptions class="mt-16" title="归属与状态" :column="2" border>
          <el-descriptions-item label="送货员编码">{{ info.riderCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="登录账号">{{ info.userName || '未开通' }}</el-descriptions-item>
          <!-- 挂靠的物流公司由平台/物流公司在送货员管理里维护，不在自助页的编辑白名单内，所以编辑态也是只读的 -->
          <el-descriptions-item label="所属物流公司">{{ info.logisticsCompanyName || '未挂靠' }}</el-descriptions-item>
          <el-descriptions-item label="最大同时接单数">{{ info.maxOrderCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">
            <el-tag size="small" :type="dict.tag('rider_status', info.status)">
              {{ dict.label('rider_status', info.status) }}
            </el-tag>
          </el-descriptions-item>
          <!-- 这三个编辑态改由下面的表单负责，只读行就先收起来 -->
          <el-descriptions-item v-if="!editing" label="交通工具">
            <el-tag size="small" :type="dict.tag('rider_vehicle_type', info.vehicleType)">
              {{ dict.label('rider_vehicle_type', info.vehicleType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="!editing" label="在线状态">
            <el-tag size="small" :type="dict.tag('rider_online_status', info.onlineStatus)">
              {{ dict.label('rider_online_status', info.onlineStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="!editing" label="工作状态">
            <el-tag size="small" :type="dict.tag('rider_work_status', info.workStatus)">
              {{ dict.label('rider_work_status', info.workStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions class="mt-16" title="接单统计" :column="2" border>
          <el-descriptions-item label="累计接单数">{{ info.totalOrderCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="累计收入">{{ validate.formatAmount(info.totalIncome) }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ info.rating ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="建档时间">{{ validate.formatTime(info.createTime, 'YYYY-MM-DD HH:mm') }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { myShopRiderApi, updateMyShopRiderApi } from '@/api/delivery/rider'
import UploadImage from '@/components/Upload/Image.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'

/** 只读展示要打到标签的几列，字典类型：rider_vehicle_type / rider_online_status / rider_work_status / rider_status */
const dict = useDict('rider_vehicle_type', 'rider_online_status', 'rider_work_status', 'rider_status')

const loading = ref(true)
const submitLoading = ref(false)
/** 查看态直接读接口给的那份档案 */
const info = ref<any>({})
/** 编辑态改的是副本，取消就不用管；保存成功后再重新拉一次 */
const form = reactive<any>({})
const editing = ref(false)

const formRef = ref<FormInstance>()

const rules = reactive<FormRules>({
  riderName: [{ required: true, message: '送货员姓名不能为空', trigger: 'blur' }],
  phone: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
})

/** 三张证件图共用一个查看器，点开后能左右翻 */
const certImages = computed(() =>
  [info.value.idCardFront, info.value.idCardBack, info.value.healthCert].filter(Boolean)
)

const certIndex = (url: string) => Math.max(0, certImages.value.indexOf(url))

const getMy = async () => {
  loading.value = true
  try {
    const { data } = await myShopRiderApi()
    info.value = data || {}
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 编辑：把只读那份拷进表单，字段要和后端 updateMyInfo 的白名单一致 */
const handleEdit = () => {
  Object.assign(form, {
    avatar: info.value.avatar,
    riderName: info.value.riderName,
    phone: info.value.phone,
    vehicleNo: info.value.vehicleNo,
    serviceCity: info.value.serviceCity,
    serviceArea: info.value.serviceArea,
    residentAddress: info.value.residentAddress,
    residentLng: info.value.residentLng,
    residentLat: info.value.residentLat,
    vehicleType: info.value.vehicleType,
    onlineStatus: info.value.onlineStatus,
    workStatus: info.value.workStatus,
    idCard: info.value.idCard,
    healthCertExpire: info.value.healthCertExpire,
    idCardFront: info.value.idCardFront,
    idCardBack: info.value.idCardBack,
    healthCert: info.value.healthCert
  })
  editing.value = true
}

/** 地图选点回填常驻经纬度（留 6 位小数）：没挪点时组件抛的 address 是空串，不会冲掉手填的地点文本 */
const handleResidentLocation = (point: any) => {
  form.residentLng = Number(Number(point.longitude).toFixed(6))
  form.residentLat = Number(Number(point.latitude).toFixed(6))
  if (point.address) form.residentAddress = point.address
}

const handleCancel = () => {
  editing.value = false
  formRef.value?.clearValidate()
}

const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateMyShopRiderApi(form)
      ElMessage.success('保存成功')
      editing.value = false
      getMy()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

onMounted(() => {
  getMy()
})
</script>

<style scoped lang="scss">
.header-actions {
  display: flex;
  align-items: center;

  :deep(.el-button + .el-button) {
    margin-left: 8px;
  }
}

/* 分组之间的间距：el-descriptions 自己不带外边距 */
.mt-16 {
  margin-top: 16px;
}

.cert-thumb {
  width: 80px;
  height: 56px;
  border-radius: 4px;
  cursor: zoom-in;
}

/* 经度 - 纬度 并排（和配送管理、仓库那几个表单同一套写法）：两个输入框等分，中间的分隔符不跟着伸缩 */
.pair-field {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-select),
  :deep(.el-input-number) {
    flex: 1;
    min-width: 0;
  }
}

/* 分隔符不参与伸缩，高度对齐输入框、文字居中，颜色压到占位符一档免得抢输入框 */
.pair-split {
  flex: 0 0 auto;
  height: var(--el-component-size, 32px);
  line-height: var(--el-component-size, 32px);
  text-align: center;
  color: var(--el-text-color-placeholder);
  user-select: none;
}
</style>
