<template>
    <div class="app-container">
        <!-- 搜索表单 -->
        <div class="search-wrapper">
            <el-form ref="queryFormRef" :model="queryParams" :inline="true">
                <el-form-item label="文件名" prop="filename">
                    <el-input v-model="queryParams.filename" placeholder="请输入文件名" clearable
                        @keyup.enter="handleQuery" />
                </el-form-item>
                <el-form-item label="文件类型" prop="ext">
                    <el-select v-model="queryParams.ext" placeholder="请选择文件类型" clearable>
                        <el-option v-for="item in fileTypeOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                    <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </div>

        <!-- 操作按钮区域 -->
        <el-card class="box-card">
            <template #header>
                <div class="card-header">
                    <ButtonGroup>
                        <el-button type="success" icon="Setting" @click="handleOpenOssConfig">云存储配置</el-button>
                        <el-button type="primary" icon="Plus" @click="fileUpload">文件上传</el-button>
                        <el-button v-permission="['sys:user:delete']" type="danger" icon="Delete"
                            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
                    </ButtonGroup>
                </div>
            </template>
            <!-- 数据表格 -->
            <el-table v-loading="loading" :data="fileList" style="width: 100%"
                @selection-change="handleSelectionChange">
                <el-table-column type="selection" width="55" align="center" />
                <el-table-column label="文件内容" align="center" prop="filename">
                    <template #default="scope">
                        <el-image v-if="scope.row.contentType.includes('image')" :preview-src-list="[scope.row.url]"
                            :close-on-press-escape="true" :initial-index="0" :preview-teleported="true"
                            :src="scope.row.url" fit="scale-down" />
                    </template>

                </el-table-column>
                <el-table-column label="文件名" align="center" prop="filename" show-overflow-tooltip />
                <el-table-column label="文件类型" align="center" prop="ext" show-overflow-tooltip />
                <el-table-column label="文件大小" align="center" prop="size">
                    <template #default="scope">
                        <span>{{ formatFileSize(scope.row.size) }} </span>
                    </template>
                </el-table-column>
                <el-table-column label="url" align="center" prop="url" width="300" show-overflow-tooltip />
                <!-- <el-table-column label="文件地址" align="center" prop="url" width="300" show-overflow-tooltip>
                    <template #default="scope">
                        <span>{{ scope.row.basePath }}{{ scope.row.path }}{{ scope.row.filename }}</span>
                    </template>
                </el-table-column> -->
                <el-table-column label="存储平台" align="center" prop="platform">
                    <template #default="scope">
                        <span v-for="item in ossOptions">
                            <el-tag :type="item.style" v-if="scope.row.platform === item.value">
                                {{ item.label }}
                            </el-tag>
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="上传时间" align="center" prop="createTime" width="150" />
                <el-table-column label="操作" align="center" width="200" fixed="right">
                    <template #default="scope">
                        <el-button v-permission="['sys:file:delete']" type="danger" link icon="Delete"
                            @click="handleDelete(scope.row)">删除</el-button>
                        <el-button type="primary" link icon="Download" @click="handleDownload(scope.row)">下载</el-button>
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
        </el-card>

        <!-- 云存储配置 -->
        <el-drawer v-model="drawerVisible" title="云存储配置" direction="rtl" size="40%">
            <el-form :model="ossConfigForm" label-position="left" label-width="100px" :rules="rules"
                ref="ossConfigFormRef">
                <el-form-item label="平台" prop="platform">
                    <el-radio-group v-model="ossConfigForm.platform" @change="handleChangePlatform">
                        <el-radio v-for="item in ossOptions" :key="item.value" :label="item.label"
                            :value="item.value" />
                    </el-radio-group>
                </el-form-item>
                <div v-if="ossConfigForm.platform !== 'local' && ossConfigForm.platform !== 'fastdfs'">
                    <el-form-item label="access-key" prop="accessKey">
                        <el-input v-model="ossConfigForm.accessKey" placeholder="请输入accessKey" />
                    </el-form-item>
                    <el-form-item label="secret-key" prop="secretKey">
                        <el-input v-model="ossConfigForm.secretKey" placeholder="请输入secretKey" />
                    </el-form-item>
                    <el-form-item label="空间名" prop="bucket">
                        <el-input v-model="ossConfigForm.bucket" placeholder="请输入空间名" />
                    </el-form-item>
                    <el-form-item label="地域" prop="region">
                        <el-input v-model="ossConfigForm.region" placeholder="请输入地域" />
                    </el-form-item>
                </div>
                <div v-if="ossConfigForm.platform == 'fastdfs'">
                    <el-form-item label="server-addr" prop="serverAddr">
                        <el-input type="textarea" autosize placeholder="Tracker Server 地址（IP:PORT），多个用英文逗号隔开"
                            v-model="ossConfigForm.serverAddr"></el-input>
                    </el-form-item>
                </div>

                <el-form-item label="域名" prop="domain">
                    <el-input v-model="ossConfigForm.domain" placeholder="请输入域名，/结尾" />
                </el-form-item>
                <el-form-item label="存储基础路径" prop="basePath">
                    <el-input v-model="ossConfigForm.basePath" placeholder="请输入存储基础路径，/结尾" />
                </el-form-item>
                <div v-if="ossConfigForm.platform === 'local'">
                    <el-form-item label="本地存储路径" prop="storagePath" label-width="120px">
                        <el-input v-model="ossConfigForm.storagePath" placeholder="请输入本地存储路径，/结尾,如 D:/Temp/" />
                    </el-form-item>
                    <el-form-item label="访问路径" v-if="ossConfigForm.enableAccess === 1" prop="pathPatterns"
                        label-width="120px">
                        <el-input v-model="ossConfigForm.pathPatterns" placeholder="访问路经要与域名后面的路径一致,并/**结尾" />
                    </el-form-item>
                    <el-form-item label="启用访问" prop="enableAccess">
                        <el-switch v-model="ossConfigForm.enableAccess" :active-value="1" :inactive-value="0" />
                    </el-form-item>
                </div>
                <el-form-item label="启用存储" prop="isEnable">
                    <el-switch v-model="ossConfigForm.isEnable" :active-value="1" :inactive-value="0" />
                </el-form-item>


            </el-form>
            <div class="dialog-footer">
                <el-button type="primary" v-permission="['sys:oss:submit']" icon="CircleCheck"
                    :loading="ossConfigLoading" @click="handleSaveOssConfig">保存</el-button>
            </div>
        </el-drawer>
        <!-- 文件上传 -->
        <el-dialog title="文件上传" v-model="uploadVisible" width="50%" top="5vh">
            <el-upload ref="uploadZipRef" class="upload-demo" method="post" drag :on-success="handleSuccess"
                :action="uploadUrl">
                <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                <div class="el-upload__text">
                    拖拽文件到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                    <div class="el-upload__tip">
                        <span>测试文件上传返回url</span>
                    </div>
                </template>
            </el-upload>
        </el-dialog>

    </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getFileListApi, deleteFileApi,batchDeleteFileApi, getOssConfigApi, addOssApi, updateOssApi } from '@/api/file'
import { getDictDataByDictTypesApi } from '@/api/system/dict'
const uploadUrl = `${import.meta.env.VITE_APP_BASE_API}/file/upload?source=test`
// 查询参数
const queryParams = reactive({
    pageNum: 1,
    pageSize: 10,
    filename: undefined,
    ext: undefined
})

const uploadZipRef = ref()
const uploadVisible = ref(false)
const loading = ref(false)
const total = ref(0)
const fileList = ref([])
const fileTypeOptions = ref<any[]>([])
const ossOptions = ref<any[]>([])

const ossConfigList = ref<any>({})
const drawerVisible = ref(false)
const ossConfigLoading = ref(false)

const ossConfigForm = ref<any>({})
const ossConfigFormRef = ref<any>(null)
// 选中项数组
const selectedIds = ref<string[]>([])


// 表单校验规则
const rules = reactive<FormRules>({
    platform: [
        { required: true, message: '平台不能为空', trigger: 'blur' }
    ],
    accessKey: [{ required: true, message: 'accessKey不能为空', trigger: 'blur' }],
    secretKey: [{ required: true, message: 'secretKey不能为空', trigger: 'blur' }],
    bucket: [{ required: true, message: 'bucket不能为空', trigger: 'blur' }],
    domain: [
        { required: true, message: '域名不能为空', trigger: 'blur' }
    ],
    basePath: [
        { required: false, message: '存储基础路径不能为空', trigger: 'blur' }
    ],
    storagePath: [
        { required: true, message: '本地存储路径不能为空', trigger: 'blur' }
    ],
    enableAccess: [
        { required: true, message: '启用访问不能为空', trigger: 'blur' }
    ],
    pathPatterns: [
        { required: true, message: '访问路径不能为空', trigger: 'blur' }
    ]
})

// 表格选择项变化
const handleSelectionChange = (selection: any[]) => {
  selectedIds.value = selection.map(item => item.url)
}

const formatFileSize = (size:number) => {
    if (!Number.isFinite(size)) {
        return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
    let index = 0;
    let num = size;
    while (num >= 1024 && index < units.length - 1) {
        num /= 1024;
        index++;
    }
    return num.toFixed(1) + ' ' + units[index];
}



// 获取文件列表
const getList = async () => {
    loading.value = true
    try {
        const { data } = await getFileListApi(queryParams)
        fileList.value = data.records
        total.value = data.total
    } catch (error) {
    }
    loading.value = false
}


// 获取状态列表
const getDictList = async () => {
    const { data } = await getDictDataByDictTypesApi(['sys_file_type', 'sys_file_oss'])
    fileTypeOptions.value = data.sys_file_type.list
    ossOptions.value = data.sys_file_oss.list
}

// 删除
const handleDelete = (row: any) => {
    ElMessageBox.confirm(`是否确认删除 ${row.filename} 这个文件?`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await deleteFileApi(row.url)
            ElMessage.success('删除成功')
            getList()
        } catch (error) {
        }
    })
}

// 批量删除
const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return
  console.log(selectedIds.value)
  
  ElMessageBox.confirm('是否确认批量删除选中的文件?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => { 
    try {
      await batchDeleteFileApi(selectedIds.value)
      ElMessage.success('批量删除成功')
      getList()
      selectedIds.value = []
    } catch (error) {
    }
  })
}

// 获取存储配置
const getOssConfig = () => {
    getOssConfigApi().then((res) => {
        ossConfigList.value = res.data
    })
}


// 打开存储配置
const handleOpenOssConfig = () => {
    if (ossOptions.value.length === 0) {
        ElMessage.warning('请先在字典添加云存储类型')
        return
    }
    ossConfigForm.value = {}

    const ossConfig = ossConfigList.value.find((item: any) => {
        if (item.isEnable === 1) {
            return item
        }
    })
    Object.assign(ossConfigForm.value, ossConfig)
    drawerVisible.value = true
}

const fileUpload = () => {
  uploadVisible.value = true
  if (uploadZipRef.value) {
    uploadZipRef.value.clearFiles()
  }
}

const handleSuccess = (response: any) => {
    if (response.code === 200) {
        uploadZipRef.value?.clearFiles()
        uploadVisible.value = false
        ElMessage.success('上传成功：'+ response.data)
    } else {
        ElMessage.error('上传失败')
    }
}

// 平台改变
const handleChangePlatform = () => {
    ossConfigForm.value.id = undefined
    ossConfigForm.value.accessKey = ''
    ossConfigForm.value.secretKey = ''
    ossConfigForm.value.bucket = ''
    ossConfigForm.value.domain = ''
    ossConfigForm.value.basePath = ''
    ossConfigForm.value.storagePath = ''
    ossConfigForm.value.region = ''
    ossConfigForm.value.isEnable = 0

    const ossConfig = ossConfigList.value.find((item: any) => {
        if (item.platform === ossConfigForm.value.platform) {
            return item
        }
    })

    if (ossConfig) {
        Object.assign(ossConfigForm.value, ossConfig)
    }
}

// 保存云存储配置
const handleSaveOssConfig = async () => {
    await ossConfigFormRef.value.validate(async (valid: any) => {
        if (valid) {
            ossConfigLoading.value = true
            try {
                if (ossConfigForm.value.id) {
                    updateOssApi(ossConfigForm.value).then(() => {
                        ElMessage.success('修改成功')
                        drawerVisible.value = false
                        getOssConfig()
                        ossConfigLoading.value = false
                    })
                } else {
                    addOssApi(ossConfigForm.value).then(() => {
                        ElMessage.success('保存成功')
                        drawerVisible.value = false
                        getOssConfig()
                        ossConfigLoading.value = false
                    })
                }
            } catch (error) {
            }
        }
    })
}
// 下载
const handleDownload = (row: any) => {
    window.open(row.url)
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

// 搜索
const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
}

// 重置
const resetQuery = () => {
    queryParams.pageNum = 1
    queryParams.filename = undefined
    queryParams.ext = undefined
    getList()
}

// 初始化
onMounted(() => {
    getList()
    getDictList()
    getOssConfig()
})
</script>