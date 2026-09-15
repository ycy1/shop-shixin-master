<template>
    <div class="wang-editor">
        <!-- 编辑器内容区域 -->
         <Toolbar style="border-bottom: 1px solid #ccc;" :editor="editorRef" :defaultConfig="toolbarConfig" :mode="mode" />
         <Editor style=" overflow-y: hidden;min-height: 300px;" v-model="modelVar" :defaultConfig="editorConfig" :mode="mode"
         @onCreated="handleCreated"/>
    </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { getToken } from '@/utils/auth'


const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
})
// 创建一个名为update:modelValue的自定义事件
const emit = defineEmits(['update:modelValue'])
// 创建一个与props.modelValue的值同步的ref
const modelVar = ref(props.modelValue)
console.log('modelVar.value:', modelVar.value);

watch(() => modelVar.value, (newVal) => {
  console.log('props.modelValue changed:', newVal)
  modelVar.value = newVal
  emit('update:modelValue', newVal)
})

onBeforeUnmount(() => {
  console.log('组件卸载前，销毁编辑器实例')
  editorRef.value?.destroy()
})

const editorRef = shallowRef()
// const content = ref('') // 用于双向绑定编辑器内容
const mode = 'default'
const toolbarConfig = {}
const editorConfig = {
  placeholder: "请输入内容...",
  MENU_CONF: {
    codeSelectLang: {
      // 代码语言
      codeLangs: [
        { text: "CSS", value: "css" },
        { text: "HTML", value: "html" },
        { text: "XML", value: "xml" },
        { text: "Java", value: "java" },
      ],
    },

    uploadImage: {
      // 图片上传配置
      server: `${import.meta.env.VITE_APP_BASE_API}/file/uploadImage?source=moment`,
      fieldName: 'file',
      headers: {
        Authorization: getToken() || ''
      },
      maxFileSize: 5 * 1024 * 1024, // 5MB
      maxNumberOfFiles: 9,
      accept: 'image/*',
      multiple: true,
      onSuccess(file: File, res: any) {
        // TS 语法
        // onSuccess(file, res) {          // JS 语法
        // console.log(`${file.name} 上传成功`, res)
      },
      onError(file: File, res: any) {
        console.error(`${file.name} 上传失败`, res)
        ElMessage.error(`上传失败: ${res.message || '未知错误'}`)
      },
      onProgress(file: File, percentage: number) {
        console.log(`${file.name} 上传进度: ${percentage}%`)
      },
      // 自定义插入图片
      customInsert(res: any, insertFn: Function) {
        console.log('自定义插入图片', res.data)
        // TS 语法
        // customInsert(res, insertFn) {                  // JS 语法
        // res 即服务端的返回结果
        if (!res || !res.data) {
          ElMessage.error('上传失败，未返回图片地址')
          return
        }
        // 从 res 中找到 url alt href ，然后插入图片
        insertFn(res.data, null, null)
      },
    },
    uploadVideo:{
      // 视频上传配置
      server: `${import.meta.env.VITE_APP_BASE_API}/file/upload?source=moment`,
      fieldName: 'file',
      headers: {
        Authorization: getToken() || ''
      },
      maxFileSize: 50 * 1024 * 1024, // 50MB
      accept: 'video/*',
      multiple: true,
      onSuccess(file: File, res: any) {
        console.log(`${file.name} 上传成功`, res)
      },
      onError(file: File, res: any) {
        console.error(`${file.name} 上传失败`, res)
        ElMessage.error(`上传失败: ${res.message || '未知错误'}`)
      },
      // 自定义插入图片
      customInsert(res: any, insertFn: Function) {
        console.log('自定义插入视频', res.data)
        // TS 语法
        // customInsert(res, insertFn) {                  // JS 语法
        // res 即服务端的返回结果
        if (!res || !res.data) {
          ElMessage.error('上传失败，未返回视频地址')
          return
        }
        // 从 res 中找到 url poster ，然后插入视频
        insertFn(res.data, null)
      },
    }
  },
}

// 富文本编辑器创建完成
const handleCreated = (editor:any) => {
  editorRef.value = editor // 记录 editor 实例，重要！
  console.log('editorRef handleCreated', editorRef.value)
//   editor.on('change', () => {
//     console.log('内容变化:', editor.getHtml())
//     emit('update:modelValue',editor.getHtml())
//   })
}


</script>

<style scoped>
.wang-editor {
    min-height: 300px;
    border: 1px solid #dcdcdc;
    padding: 16px;
    background: #fff;
    border-radius: 4px;
}
</style>