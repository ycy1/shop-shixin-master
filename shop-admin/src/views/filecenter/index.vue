<template>
    <div class="app-container">
        <el-card>
            <div class="filecenter-layout">
                <!-- 垂直 Tab：我的文件 / 部门文件 / 角色文件 / 回收站 -->
                <el-tabs v-model="activeTab" tab-position="left" class="filecenter-tabs">
                    <el-tab-pane v-for="item in tabOptions" :key="item.value" :label="item.label"
                        :name="String(item.value)" />
                </el-tabs>

                <!-- 右侧内容区 -->
                <div class="filecenter-content">
                    <!-- 搜索表单 -->
                    <div class="search-wrapper">
                        <el-form ref="queryFormRef" :model="queryParams" :inline="true">
                            <el-form-item label="文件名称" prop="fileName">
                                <el-input v-model="queryParams.fileName" placeholder="请输入文件名称" clearable
                                    @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="业务类型" prop="businessType">
                                <el-select v-model="queryParams.businessType" placeholder="请选择业务类型" clearable
                                    style="width: 140px">
                                    <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label"
                                        :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="文件状态" prop="fileStatus">
                                <el-select v-model="queryParams.fileStatus" placeholder="请选择文件状态" clearable
                                    style="width: 140px">
                                    <el-option v-for="item in fileStatusOptions" :key="item.value" :label="item.label"
                                        :value="item.value" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="上传时间">
                                <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
                                    start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
                            </el-form-item>
                            <el-form-item>
                                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                            </el-form-item>
                        </el-form>
                    </div>

                    <!-- 操作按钮区域 -->
                    <div class="card-header">
                        <ButtonGroup>
                            <el-button v-if="activeTab !== '4'" v-permission="['sys:filecenter:upload']" type="primary"
                                icon="Plus" @click="openUploadDialog">上传文件</el-button>
                            <!-- 普通 Tab：批量删除（移入回收站） -->
                            <el-button v-if="activeTab !== '4'" v-permission="['sys:filecenter:delete']" type="danger"
                                icon="Delete" :disabled="selectedIds.length === 0"
                                @click="handleBatchDelete">批量删除</el-button>
                            <!-- 普通 Tab：批量设置过期时间 -->
                            <el-button v-if="activeTab !== '4'" v-permission="['sys:filecenter:expire']" type="warning"
                                icon="Timer" :disabled="selectedIds.length === 0"
                                @click="handleBatchSetExpire">批量设置过期时间</el-button>
                            <!-- 回收站：批量还原 -->
                            <el-button v-if="activeTab === '4'" type="success" icon="RefreshLeft"
                                :disabled="selectedIds.length === 0"
                                @click="handleBatchRestore">批量还原</el-button>
                        </ButtonGroup>
                    </div>

                    <!-- 数据表格 -->
                    <el-table ref="tableRef" v-loading="loading" :data="fileList" style="width: 100%"
                        @selection-change="handleSelectionChange">
                        <el-table-column type="selection" align="center" width="55" />
                        <el-table-column label="文件名称" align="center" prop="fileName" show-overflow-tooltip />
                        <el-table-column label="所属用户" align="center" prop="ownerNickname" show-overflow-tooltip>
                            <template #default="scope">
                                <span>{{ scope.row.ownerNickname || scope.row.ownerUsername || '-' }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="文件大小" align="center" prop="fileSize">
                            <template #default="scope">
                                <span>{{ formatFileSize(scope.row.fileSize) }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="业务类型" align="center" prop="businessType">
                            <template #default="scope">
                                <el-tag v-if="getBusinessType(scope.row.businessType)"
                                    :type="getBusinessType(scope.row.businessType).style">
                                    {{ getBusinessType(scope.row.businessType).label }}
                                </el-tag>
                                <span v-else>-</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="文件状态" align="center" prop="fileStatus">
                            <template #default="scope">
                                <el-tag v-if="scope.row.fileStatus !== null && scope.row.fileStatus !== undefined"
                                    :type="getStatusStyle(scope.row.fileStatus)">
                                    {{ getStatusLabel(scope.row.fileStatus) }}
                                </el-tag>
                                <span v-else>-</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="上传时间" align="center" prop="createTime" width="170" />
                        <el-table-column label="剩余过期时间" align="center" width="120">
                            <template #default="scope">
                                <el-tag v-if="getRemainExpire(scope.row.expireTime)"
                                    :type="getRemainExpire(scope.row.expireTime).style">
                                    {{ getRemainExpire(scope.row.expireTime).label }}
                                </el-tag>
                                <span v-else>-</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="下载次数" align="center" prop="downloadCount" width="90" />
                        <el-table-column label="操作" align="center" width="250" fixed="right">
                            <template #default="scope">
                                <!-- 回收站：还原 + 彻底删除 -->
                                <template v-if="activeTab === '4'">
                                    <el-button v-if="scope.row.fileStatus !== 4" type="success" link icon="RefreshLeft"
                                        @click="handleRestore(scope.row)">还原</el-button>
                                    <el-button type="danger" link icon="Delete" v-permission="['sys:filecenter:delete']"
                                        @click="handleDeleteForever(scope.row)">彻底删除</el-button>
                                </template>
                                <!-- 普通 Tab：下载 + 改期 + 删除（处理中不展示下载/改期） -->
                                <template v-else>
                                    <el-button
                                        v-if="scope.row.fileStatus !== 1 && hasPermission('sys:filecenter:download')"
                                        type="primary" link icon="Download"
                                        :loading="downloadLoadingId === scope.row.id"
                                        @click="handleDownload(scope.row)">下载</el-button>
                                    <el-button v-if="scope.row.fileStatus !== 1" type="warning" link icon="Edit"
                                        v-permission="['sys:filecenter:expire']"
                                        @click="handleSetExpire(scope.row)">改期</el-button>
                                    <el-button type="danger" link icon="Delete" v-permission="['sys:filecenter:delete']"
                                        @click="handleDelete(scope.row)">删除</el-button>
                                </template>
                            </template>
                        </el-table-column>
                    </el-table>

                    <!-- 分页组件 -->
                    <div class="pagination-container">
                        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
                            :page-sizes="[10, 20, 30, 50]" :total="total" :background="true"
                            layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
                            @current-change="handleCurrentChange" />
                    </div>
                </div>
            </div>
        </el-card>

        <!-- 上传弹窗 -->
        <el-dialog title="上传文件" v-model="uploadDialog.visible" width="480px" top="10vh">
            <el-form :model="uploadForm" label-width="80px">
                <!-- <el-form-item label="业务类型" required>
                    <el-select v-model="uploadForm.businessType" placeholder="请选择业务类型" style="width: 100%">
                        <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item> -->
                <el-form-item label="文件" required>
                    <el-upload ref="uploadRef" class="upload-demo" drag :auto-upload="false" :limit="1"
                        :on-change="handleUploadChange" :on-remove="handleUploadRemove" :on-exceed="handleUploadExceed">
                        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                        <div class="el-upload__text">
                            拖拽文件到此处，或<em>点击选择文件</em>
                        </div>
                        <template #tip>
                            <div class="el-upload__tip">
                                <span>文件将上传至「{{ currentTabLabel }}」</span>
                            </div>
                        </template>
                    </el-upload>
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="uploadDialog.visible = false">取消</el-button>
                    <el-button type="primary" icon="Upload" :loading="uploadLoading"
                        @click="handleUploadSubmit">上传</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 设置过期时间弹窗（单个/批量共用） -->
        <el-dialog title="设置过期时间" v-model="expireDialog.visible" width="420px" top="30vh">
            <el-form :model="expireForm" label-width="90px">
                <el-form-item label="过期时间" required>
                    <el-date-picker v-model="expireForm.expireTime" type="datetime" placeholder="请选择过期时间"
                        value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="disabledExpireDate"
                        style="width: 100%" />
                </el-form-item>
                <el-alert v-if="expireTargetIds.length > 1" type="info" :closable="false"
                    :title="`将同时设置选中的 ${expireTargetIds.length} 个文件`" />
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="expireDialog.visible = false">取消</el-button>
                    <el-button type="primary" :loading="expireLoading" @click="handleExpireSubmit">确定</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import {
    getFileCenterPageApi,
    uploadFileCenterApi,
    downloadFileCenterApi,
    deleteFileCenterApi,
    restoreFileCenterApi,
    deleteFileCenterForeverApi,
    updateFileCenterExpireApi
} from '@/api/filecenter'
import { getDictDataDictTypeCacheApi } from '@/api/system/dict'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

// 权限检查
const hasPermission = (permission: string): boolean => {
    return permissions.value.includes(permission)
}

// Tab 选项
const tabOptions = [
    { value: 1, label: '我的文件' },
    { value: 2, label: '部门文件' },
    { value: 3, label: '角色文件' },
    { value: 4, label: '回收站' }
]

// 文件状态
const fileStatusOptions = [
    { value: 1, label: '处理中', style: 'info' },
    { value: 2, label: '已完成', style: 'success' },
    { value: 3, label: '失败', style: 'danger' },
    { value: 4, label: '已过期', style: 'warning' }
]

const activeTab = ref('1')
const currentTabLabel = computed(() => {
    const tab = tabOptions.find(item => String(item.value) === activeTab.value)
    return tab ? tab.label : ''
})

// 查询参数
const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    fileName: '',
    businessType: undefined,
    fileStatus: undefined as number | undefined,
    startTime: undefined,
    endTime: undefined
})
const dateRange = ref<[string, string]>()

// 监听日期范围变化
watch(dateRange, (val) => {
    if (val) {
        queryParams.startTime = val[0]
        queryParams.endTime = val[1]
    } else {
        queryParams.startTime = undefined
        queryParams.endTime = undefined
    }
})

// 切换 Tab 时回到第一页重新查询，并清空选中
watch(activeTab, () => {
    queryParams.pageNum = 1
    clearSelection()
    getList()
})

const loading = ref(false)
const total = ref(0)
const fileList = ref<any[]>([])
const businessTypeOptions = ref<any[]>([])

const tableRef = ref<any>(null)
// 选中的文件 id（用于批量删除/还原）
const selectedIds = ref<number[]>([])

// 清除选中（含表格勾选）
const clearSelection = () => {
    selectedIds.value = []
    tableRef.value?.clearSelection()
}

// 当前正在下载的文件 id（控制下载按钮 loading）
const downloadLoadingId = ref<number | null>(null)

const uploadDialog = reactive({ visible: false })
const uploadForm = reactive<any>({ file: null })
const uploadLoading = ref(false)
const uploadRef = ref<any>(null)

// 设置过期时间弹窗（单个/批量共用）
const expireDialog = reactive({ visible: false })
const expireForm = reactive<{ expireTime?: string }>({ expireTime: undefined })
const expireTargetIds = ref<number[]>([])
const expireLoading = ref(false)

// 禁用早于今天的日期（允许当天）
const disabledExpireDate = (time: Date) => {
    return time.getTime() < new Date(new Date().setHours(0, 0, 0, 0)).getTime()
}

// 剩余过期时间：不足一天按小时显示
const getRemainExpire = (expireTime: string) => {
    if (!expireTime) {
        return null
    }
    const expire = new Date(expireTime.replace(/-/g, '/')).getTime()
    const diff = expire - Date.now()
    if (diff <= 0) {
        return { label: '已过期', style: 'danger' }
    }
    const hours = Math.floor(diff / 3600000)
    if (hours < 24) {
        return { label: hours + ' 小时', style: 'warning' }
    }
    return { label: Math.floor(hours / 24) + ' 天', style: 'success' }
}

// 单条：打开设置过期时间弹窗
const handleSetExpire = (row: any) => {
    expireTargetIds.value = [row.id]
    expireForm.expireTime = row.expireTime || undefined
    expireDialog.visible = true
}

// 批量：打开设置过期时间弹窗
const handleBatchSetExpire = () => {
    if (selectedIds.value.length === 0) {
        ElMessage.warning('请先选择要设置的文件')
        return
    }
    expireTargetIds.value = [...selectedIds.value]
    expireForm.expireTime = undefined
    expireDialog.visible = true
}

// 提交设置过期时间
const handleExpireSubmit = async () => {
    if (!expireForm.expireTime) {
        ElMessage.warning('请选择过期时间')
        return
    }
    const expireTime = expireForm.expireTime
    expireLoading.value = true
    try {
        await updateFileCenterExpireApi({
            ids: expireTargetIds.value,
            expireTime
        })
        ElMessage.success('设置成功')
        expireDialog.visible = false
        clearSelection()
        getList()
    } catch (error) {
    }
    expireLoading.value = false
}

// 获取字典
const getDictList = async () => {
    const result = await getDictDataDictTypeCacheApi('sys_file_center_business')
    businessTypeOptions.value = result.data
}

// 获取文件列表
const getList = async () => {
    loading.value = true
    try {
        const params: any = { ...queryParams, source: Number(activeTab.value) }
        const { data } = await getFileCenterPageApi(params)
        fileList.value = data.records
        total.value = data.total
    } catch (error) {
    }
    loading.value = false
}

// 文件大小格式化（B → PB）
const formatFileSize = (size: number) => {
    if (!Number.isFinite(size)) {
        return '-'
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    let index = 0
    let num = size
    while (num >= 1024 && index < units.length - 1) {
        num /= 1024
        index++
    }
    return num.toFixed(1) + ' ' + units[index]
}

// 业务类型
const getBusinessType = (value: string) => {
    return businessTypeOptions.value.find(item => item.value === value)
}

// 文件状态
const getStatusLabel = (value: number) => {
    const item = fileStatusOptions.find(opt => opt.value === value)
    return item ? item.label : '-'
}
const getStatusStyle = (value: number) => {
    const item = fileStatusOptions.find(opt => opt.value === value)
    return item ? item.style : 'info'
}

// 搜索
const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
}

// 重置
const resetQuery = () => {
    dateRange.value = undefined
    queryParams.pageNum = 1
    queryParams.fileName = ''
    queryParams.businessType = undefined
    queryParams.fileStatus = undefined
    queryParams.startTime = undefined
    queryParams.endTime = undefined
    getList()
}

// 分页大小改变
const handleSizeChange = (val: number) => {
    queryParams.pageSize = val
    getList()
}

// 页码改变
const handleCurrentChange = (val: number) => {
    queryParams.pageNum = val
    getList()
}

// 打开上传弹窗
const openUploadDialog = () => {
    uploadForm.file = null
    uploadRef.value?.clearFiles()
    uploadDialog.visible = true
}

// 选择文件
const handleUploadChange = (file: any) => {
    console.log(file)
    uploadForm.file = file.raw
}

// 移除文件
const handleUploadRemove = () => {
    uploadForm.file = null
}

// 超出限制
const handleUploadExceed = (files: any[]) => {
    uploadRef.value?.clearFiles()
    uploadRef.value?.handleStart(files[0])
}

// 提交上传
const handleUploadSubmit = async () => {
    // if (!uploadForm.businessType) {
    //     ElMessage.warning('请选择业务类型')
    //     return
    // }
    console.log(uploadForm)
    if (!uploadForm.file) {
        ElMessage.warning('请选择要上传的文件')
        return
    }
    uploadLoading.value = true
    try {
        const formData = new FormData()
        formData.append('file', uploadForm.file)
        formData.append('fileSource', activeTab.value)
        await uploadFileCenterApi(formData)
        ElMessage.success('上传成功')
        uploadDialog.visible = false
        getList()
    } catch (error) {
    }
    uploadLoading.value = false
}

// 下载
const handleDownload = async (row: any) => {
    downloadLoadingId.value = row.id
    try {
        const blob: any = await downloadFileCenterApi(row.id)
        // 后端异常返回 JSON，避免把错误信息存成文件
        if (blob && blob.type && blob.type.includes('application/json')) {
            const text = await blob.text()
            const res = JSON.parse(text)
            ElMessage.error(res.message || '下载失败')
            return
        }
        saveAs(blob, row.fileName || 'file')
    } catch (error) {
    } finally {
        downloadLoadingId.value = null
    }
}

// 删除（移入回收站）
const handleDelete = (row: any) => {
    ElMessageBox.confirm(`是否确认将 ${row.fileName} 移入回收站？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await deleteFileCenterApi(row.id)
            ElMessage.success('已移入回收站')
            getList()
        } catch (error) {
        }
    }).catch(() => {
    })
}

// 还原
const handleRestore = (row: any) => {
    ElMessageBox.confirm(`是否确认还原 ${row.fileName}？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
    }).then(async () => {
        try {
            await restoreFileCenterApi(row.id)
            ElMessage.success('还原成功')
            getList()
        } catch (error) {
        }
    }).catch(() => {
    })
}

// 彻底删除
const handleDeleteForever = (row: any) => {
    ElMessageBox.confirm(`彻底删除 ${row.fileName} 后，文件记录与存储文件都将被删除且不可恢复，是否继续？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await deleteFileCenterForeverApi(row.id)
            ElMessage.success('删除成功')
            getList()
        } catch (error) {
        }
    }).catch(() => {
    })
}

// 多选变化
const handleSelectionChange = (rows: any[]) => {
    selectedIds.value = rows.map(row => row.id)
}

// 批量删除（移入回收站）
const handleBatchDelete = () => {
    if (selectedIds.value.length === 0) {
        ElMessage.warning('请先选择要删除的文件')
        return
    }
    ElMessageBox.confirm(`是否确认将选中的 ${selectedIds.value.length} 个文件移入回收站？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await deleteFileCenterApi(selectedIds.value)
            ElMessage.success('已移入回收站')
            clearSelection()
            getList()
        } catch (error) {
        }
    }).catch(() => {
    })
}

// 批量还原
const handleBatchRestore = () => {
    if (selectedIds.value.length === 0) {
        ElMessage.warning('请先选择要还原的文件')
        return
    }
    ElMessageBox.confirm(`是否确认还原选中的 ${selectedIds.value.length} 个文件？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
    }).then(async () => {
        try {
            await restoreFileCenterApi(selectedIds.value)
            ElMessage.success('还原成功')
            clearSelection()
            getList()
        } catch (error) {
        }
    }).catch(() => {
    })
}

// 初始化
onMounted(() => {
    getList()
    getDictList()
})
</script>

<style scoped>
.filecenter-layout {
    display: flex;
}

.filecenter-tabs {
    width: 140px;
    flex-shrink: 0;
}

.filecenter-content {
    flex: 1;
    min-width: 0;
    margin-left: 8px;
}
</style>
