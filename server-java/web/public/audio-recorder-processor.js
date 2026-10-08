/**
 * AudioWorklet 处理器 - 用于录音
 * 替代已废弃的 ScriptProcessorNode
 */
class AudioRecorderProcessor extends AudioWorkletProcessor {
  constructor() {
    super()
    this.bufferSize = 4096
    this.buffer = []
    this.bufferLength = 0
  }

  process(inputs, outputs, parameters) {
    const input = inputs[0]
    
    // 如果有输入音频数据
    if (input && input.length > 0) {
      const channelData = input[0] // 获取第一个声道
      
      if (channelData && channelData.length > 0) {
        // 复制音频数据（避免引用问题）
        const copy = new Float32Array(channelData.length)
        copy.set(channelData)
        
        // 添加到缓冲区
        this.buffer.push(copy)
        this.bufferLength += copy.length
        
        // 当缓冲区达到指定大小时，发送数据
        if (this.bufferLength >= this.bufferSize) {
          // 合并缓冲区数据
          const mergedBuffer = new Float32Array(this.bufferLength)
          let offset = 0
          
          for (const buf of this.buffer) {
            mergedBuffer.set(buf, offset)
            offset += buf.length
          }
          
          // 发送音频数据到主线程
          this.port.postMessage({
            type: 'audio-data',
            data: mergedBuffer
          })
          
          // 清空缓冲区
          this.buffer = []
          this.bufferLength = 0
        }
      }
    }
    
    // 返回 true 表示继续处理
    return true
  }
}

// 注册处理器
registerProcessor('audio-recorder-processor', AudioRecorderProcessor)

