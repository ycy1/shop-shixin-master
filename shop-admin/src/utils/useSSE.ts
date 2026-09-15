// composables/useSSE.ts
import { ref } from 'vue';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getToken } from '@/utils/auth'
import { renderStreamingMarkdown,renderMarkdown } from '@/utils/markdown'

export function useSSE() {
  const data = ref('');              // 原始流式数据
  const rendered = ref('');          // Markdown 渲染后的 HTML
  const isStreaming = ref(false);    // 连接状态
  let abortController: AbortController | null = null;
  let retryCount = 0;
  const MAX_RETRIES = 5;

  const start = async (url: string, options: any, onData: (data: string) => void) => {
    abortController = new AbortController();
    isStreaming.value = true;
    data.value = '';
    rendered.value = '';
    retryCount = 0;
    const token = getToken()
    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': token ?? ''
        },
        body: JSON.stringify(options.body),
        signal: abortController.signal,
        openWhenHidden: true,

        onmessage(event) {
          retryCount = 0;  // 收到消息时重置重试计数
          onData(event.data);
          if (event.data === '') {
            data.value += '\\n'
          }else{
            data.value += event.data;
          }
        },

        onerror(err) {
          retryCount++;
          console.error(`SSE错误 (${retryCount}/${MAX_RETRIES})`, err);
          if (retryCount >= MAX_RETRIES) {
            isStreaming.value = false;
            abortController?.abort();
            return;  // 不抛出异常，停止重试
          }
          return;  // 允许重试
        },

        onclose() {
          isStreaming.value = false;
        }
      });
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        console.log('主动中止请求');
      } else {
        console.error('连接失败', error);
      }
      isStreaming.value = false;
    }
  };

  const stop = () => {
    if (abortController) {
      abortController.abort();
      isStreaming.value = false;
    }
  };

  return { aiData: data, rendered, isStreaming, start, stop };
}