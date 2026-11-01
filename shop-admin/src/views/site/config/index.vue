<template>
  <div class="app-container">
    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="网站Logo" prop="logo">
              <upload-image v-model="form.logo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网站名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入网站名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="网站介绍" prop="summary">
              <el-input v-model="form.summary" type="textarea" placeholder="请输入网站介绍" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备案号" prop="recordNum">
              <el-input v-model="form.recordNum" placeholder="请输入备案号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="网站地址" prop="webUrl">
              <el-input v-model="form.webUrl" placeholder="请输入网站地址" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 作者信息（社交信息并在这一档） -->
        <el-divider content-position="left">作者信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="作者头像" prop="authorAvatar">
              <upload-image v-model="form.authorAvatar" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者名称" prop="author">
              <el-input v-model="form.author" placeholder="请输入作者名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="个性签名" prop="authorInfo">
              <el-input v-model="form.authorInfo" placeholder="请输入个性签名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Github地址" prop="github">
              <el-input v-model="form.github" placeholder="请输入Github地址">
                <template #prefix>
                  <el-icon>
                    <ElementPlus />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="Gitee地址" prop="gitee">
              <el-input v-model="form.gitee" placeholder="请输入Gitee地址">
                <template #prefix>
                  <el-icon>
                    <Platform />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="QQ号" prop="qqNumber">
              <el-input v-model="form.qqNumber" placeholder="请输入QQ号">
                <template #prefix>
                  <el-icon>
                    <ChatDotRound />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="QQ群" prop="qqGroup">
              <el-input v-model="form.qqGroup" placeholder="请输入QQ群">
                <template #prefix>
                  <el-icon>
                    <User />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="微信" prop="wechat">
              <el-input v-model="form.wechat" placeholder="请输入微信号">
                <template #prefix>
                  <el-icon>
                    <ChatLineRound />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱地址">
                <template #prefix>
                  <el-icon>
                    <Message />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关于我" prop="aboutMe" class="item-editor">
          <div style="border: 1px solid #ccc;">
              <Toolbar style="border-bottom: 1px solid #ccc;" :editor="editorRef" :defaultConfig="toolbarConfig" :mode="mode" />
              <Editor style="overflow-y: hidden;" v-model="form.aboutMe" :defaultConfig="editorConfig" :mode="mode"
              @onCreated="handleCreated"/>
          </div>
        </el-form-item>

        <!-- 网站设置 -->
        <el-divider content-position="left">网站设置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="游客头像" prop="touristAvatar">
              <upload-image v-model="form.touristAvatar" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="显示的社交信息" prop="showList">
              <el-select v-model="showList" multiple placeholder="请选择要显示的社交信息">
                <el-option label="邮箱" value="email" />
                <el-option label="QQ" value="qq" />
                <el-option label="QQ群" value="qqGroup" />
                <el-option label="Github" value="github" />
                <el-option label="Gitee" value="gitee" />
                <el-option label="微信" value="wechat" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="登录方式" prop="loginTypeList">
              <el-select v-model="loginTypeList" multiple placeholder="请选择登录方式">
                <el-option v-for="item in loginTypes" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="开启评论">
              <el-switch v-model="form.openComment" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开启赞赏">
              <el-switch v-model="form.openAdmiration" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开启灯笼">
              <el-switch v-model="form.openLantern" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
         <!--<el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="公告" prop="bulletin">
              <el-input v-model="form.bulletin" type="textarea" :rows="3" placeholder="请输入公告内容" />
            </el-form-item>
          </el-col>
        </el-row>-->
      </el-form>
    </el-card>

    <div class="action-bar">
      <el-button icon="Refresh" type="primary" v-permission="['sys:web:update']" @click="submitForm">保存配置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import UploadImage from '@/components/Upload/Image.vue'
import { getWebConfigApi, updateWebConfigApi } from '@/api/site/config'
import { getDictDataByDictTypesApi } from '@/api/system/dict'
import { uploadApi } from '@/api/file'

import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
const editorRef = shallowRef()
const mode = 'default'
const toolbarConfig = {}
const editorConfig = {
  placeholder: "请输入内容...",
  MENU_CONF: {
    // 配置上传图片
    uploadImage: {
      customUpload: contentUpload,
    },

    codeSelectLang: {
      // 代码语言
      codeLangs: [
        { text: "CSS", value: "css" },
        { text: "HTML", value: "html" },
        { text: "XML", value: "xml" },
        { text: "Java", value: "java" },
        // 其他
      ],
    },
  },
}


const formRef = ref<FormInstance>()
const form = ref({
  logo: '',
  name: '',
  summary: '',
  recordNum: '',
  webUrl: '',
  author: '',
  authorInfo: '',
  authorAvatar: '',
  github: '',
  gitee: '',
  qqNumber: '',
  qqGroup: '',
  wechat: '',
  email: '',
  showList: '',
  loginTypeList: '',
  openComment: 1,
  openAdmiration: 1,
  touristAvatar: '',
  bulletin: '',
  aboutMe: '',
  openLantern: 0
})
const showList = ref([])
const loginTypeList = ref([])
const loginTypes = ref<any>([])

const files = ref();

const rules = {
  name: [{ required: true, message: '请输入网站名称', trigger: 'blur' }],
  logo: [{ required: true, message: '请上传网站Logo', trigger: 'change' }],
  summary: [{ required: true, message: '请输入网站介绍', trigger: 'blur' }],
  recordNum: [{ required: true, message: '请输入备案号', trigger: 'blur' }],
  author: [{ required: true, message: '请输入作者名称', trigger: 'blur' }]
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (valid) {
      form.value.showList = JSON.stringify(showList.value)
      form.value.loginTypeList = JSON.stringify(loginTypeList.value)
      updateWebConfigApi(form.value).then(() => {
        ElMessage.success('保存成功')
      })
    }
  })
}
// 获取字典数据
const getDictDataByDictTypes = async () => {
  const res = await getDictDataByDictTypesApi(['login_type'])
  loginTypes.value = res.data.login_type.list
}

const handleCreated = (editor:any) => {
  editorRef.value = editor // 记录 editor 实例，重要！
}

//编辑器上传图片
function contentUpload(file: any, insertFn: any) {
  files.value = file;
  // FormData 对象
  var formData = new FormData();
  // 文件对象
  formData.append("file", files.value);
  uploadApi(formData).then((res: any) => {
    insertFn(res.data, "", res.data);
  });
}

onMounted(() => {
  getWebConfigApi().then((res) => {
    form.value = res.data
    if (form.value.showList) {
      showList.value = JSON.parse(form.value.showList)
    }
    if (form.value.loginTypeList) {
      loginTypeList.value = JSON.parse(form.value.loginTypeList)
    }
  })

  getDictDataByDictTypes();
})
</script>

<style scoped>
.app-container {
  padding: 10px;
}

/* 保存那颗吸在内容区右下角。滚动的是布局那层 .main-container（overflow-y:auto），
   而 el-card 自己带 overflow:hidden ⇒ 这一条必须放在卡片外面，塞进卡片里 sticky 就失效了。
   bottom 给的是负的 16px = .main-container 自己的 padding：sticky 的贴边是贴它的内容盒底，
   不抵消的话条下面会一直露着 16px，表单内容从那条缝里穿过去。 */
.action-bar {
  position: sticky;
  bottom: -16px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 6px 0;
  background-color: var(--el-bg-color);
  box-shadow: 0 -1px 0 var(--el-border-color-lighter);
}

:deep(.el-input-group__prepend) {
  padding: 0 10px;
}

.el-form-item {
  max-width: 600px;
}

/* 「关于我」那一格跟着表单一起只有 600px 宽，富文本挤在里面没法看，
   放到 1000px —— 跟公告那条链路的弹窗同宽（那边是 el-dialog width="1000px"）。
   这条必须排在上面那条后面：两个都是单类名，靠顺序决胜。 */
.item-editor {
  max-width: 1000px;
}
</style>
