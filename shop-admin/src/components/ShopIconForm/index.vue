<template>
  <el-dialog v-model="dialogVisible" :title="isEdit ? '修改图标' : '添加图标'" width="640px" append-to-body
    destroy-on-close top="5vh" @open="handleOpen">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="图标名称" prop="iconName">
            <el-input v-model="form.iconName" placeholder="如：向左箭头" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="图标编码" prop="iconCode">
            <el-input v-model="form.iconCode" placeholder="唯一标识，如 icon-arrow-left" maxlength="64" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="所属类目" prop="categoryId">
            <shop-category-select v-model="form.categoryId" :data="categoryOptions" placeholder="可不选"
              style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="图标类型" prop="iconType">
        <el-radio-group v-model="form.iconType" @change="handleTypeChange">
          <el-radio v-for="item in ICON_TYPES" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="图标内容" prop="iconValue">
        <!-- 三种类型的内容格式互不兼容，控件跟着切换 -->
        <el-input v-if="form.iconType === 1" v-model="form.iconValue" type="textarea" :rows="6"
          placeholder="粘贴 SVG 源码，需以 svg 标签开头并带 viewBox 属性" />
        <upload-image v-else-if="form.iconType === 2" v-model="form.iconValue" source="icon" />
        <el-input v-else-if="form.iconType === 3" v-model="form.iconValue"
          placeholder="字体类名，如 iconfont icon-shouye，多个类名用空格分隔" maxlength="255" />
      </el-form-item>
      <el-form-item label="预览">
        <div class="icon-preview">
          <shop-icon :type="form.iconType" :value="form.iconValue" :size="48" :alt="form.iconName" />
        </div>
        <div class="icon-preview-tip">
          SVG 会先做白名单清洗再渲染，script、事件属性、外部引用会被剔除，所以预览和实际显示一致。
        </div>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="255"
          show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { addShopIconApi, detailShopIconApi, updateShopIconApi } from '@/api/shop/icon'
import { getCategoryTreeApi } from '@/api/shop/category'
import ShopIcon from '@/components/ShopIcon/index.vue'
import { ICON_TYPES } from '@/components/ShopIcon/constants'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import UploadImage from '@/components/Upload/Image.vue'

const props = defineProps<{
  visible: boolean
  /** 传了 id 就是修改模式，打开时按 id 取详情；不传是新增 */
  id?: number | string
  /** 新增时预填的所属类目：图标选择器按类目筛选时，新建的图标要落在同一个类目里 */
  presetCategoryId?: number | string
}>()

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  /** 保存成功后把整条记录带出去，调用方可以直接选中它，不用再发一次详情请求 */
  saved: [icon: any]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const isEdit = computed(() => props.id !== undefined && props.id !== null && props.id !== '')

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const categoryOptions = ref<any[]>([])
const form = reactive<any>({})

const rules = reactive<FormRules>({
  iconName: [{ required: true, message: '图标名称不能为空', trigger: 'blur' }],
  iconCode: [{ required: true, message: '图标编码不能为空', trigger: 'blur' }],
  iconType: [{ required: true, message: '请选择图标类型', trigger: 'change' }],
  iconValue: [{ required: true, message: '图标内容不能为空', trigger: 'blur' }]
})

/** 切换类型时清空内容：SVG 源码当图片 URL 提交必然渲染不出来 */
const handleTypeChange = () => {
  form.iconValue = ''
}

const loadCategories = async () => {
  if (categoryOptions.value.length) return
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
    categoryOptions.value = []
  }
}

// 每个字段都显式给初值，所以只清校验红字，不调 resetFields——那会回到首次挂载时记住的值
const reset = () => {
  Object.assign(form, {
    id: undefined,
    iconName: undefined,
    iconCode: undefined,
    iconType: 1,
    iconValue: undefined,
    categoryId: props.presetCategoryId,
    sort: 0,
    status: 1,
    remark: undefined
  })
  formRef.value?.clearValidate()
}

const handleOpen = async () => {
  reset()
  loadCategories()
  if (!isEdit.value) return
  loading.value = true
  try {
    const { data } = await detailShopIconApi(props.id)
    Object.assign(form, data)
  } catch (error) {
    dialogVisible.value = false
  } finally {
    loading.value = false
  }
}

const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateShopIconApi(form)
        ElMessage.success('修改成功')
        emit('saved', { ...form })
      } else {
        // 新增接口回的是自增主键，补进记录里调用方才拿得到
        const { data } = await addShopIconApi(form)
        ElMessage.success('新增成功')
        emit('saved', { ...form, id: data })
      }
      dialogVisible.value = false
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.icon-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.icon-preview-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
