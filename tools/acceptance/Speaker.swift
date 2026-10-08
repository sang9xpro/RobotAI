// Play PCM WAV through the built-in speaker for acoustic phone acceptance.
// This queue chooses its own device; it never changes the user's default output.
import Foundation
import AudioToolbox
import CoreAudio
func checked(_ status: OSStatus) { if status != noErr { fputs("Audio error: \(status)\n", stderr); exit(1) } }
func address(_ selector: AudioObjectPropertySelector) -> AudioObjectPropertyAddress {
    AudioObjectPropertyAddress(mSelector: selector, mScope: kAudioObjectPropertyScopeGlobal, mElement: kAudioObjectPropertyElementMain)
}
var devicesAddress=address(kAudioHardwarePropertyDevices), bytes:UInt32=0
checked(AudioObjectGetPropertyDataSize(AudioObjectID(kAudioObjectSystemObject), &devicesAddress, 0, nil, &bytes))
var devices=[AudioDeviceID](repeating:0,count:Int(bytes)/MemoryLayout<AudioDeviceID>.size)
checked(AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &devicesAddress, 0, nil, &bytes, &devices))
var selected:CFString?
for device in devices {
    var nameAddress=address(kAudioObjectPropertyName), name:CFString="" as CFString, size=UInt32(MemoryLayout<CFString>.size)
    checked(AudioObjectGetPropertyData(device,&nameAddress,0,nil,&size,&name))
    if (name as String).contains("Mac mini Speakers") || (name as String).contains("MacBook") && (name as String).contains("Speakers") {
        var uidAddress=address(kAudioDevicePropertyDeviceUID), uid:CFString="" as CFString
        checked(AudioObjectGetPropertyData(device,&uidAddress,0,nil,&size,&uid));selected=uid;break
    }
}
guard var uid=selected, CommandLine.arguments.count==2 else { fputs("Built-in speakers or WAV missing\n",stderr);exit(1) }
var file:AudioFileID?
checked(AudioFileOpenURL(URL(fileURLWithPath:CommandLine.arguments[1]) as CFURL,.readPermission,0,&file))
var format=AudioStreamBasicDescription(), formatSize=UInt32(MemoryLayout<AudioStreamBasicDescription>.size)
checked(AudioFileGetProperty(file!,kAudioFilePropertyDataFormat,&formatSize,&format))
guard format.mFormatID==kAudioFormatLinearPCM else { exit(1) }
var count:UInt64=0, countSize=UInt32(MemoryLayout<UInt64>.size)
checked(AudioFileGetProperty(file!,kAudioFilePropertyAudioDataByteCount,&countSize,&count))
var queue:AudioQueueRef?
checked(AudioQueueNewOutput(&format,{ _,_,_ in },nil,nil,nil,0,&queue))
checked(AudioQueueSetProperty(queue!,kAudioQueueProperty_CurrentDevice,&uid,UInt32(MemoryLayout<CFString>.size)))
checked(AudioQueueSetParameter(queue!,kAudioQueueParam_Volume,1))
var buffer:AudioQueueBufferRef?
checked(AudioQueueAllocateBuffer(queue!,UInt32(count),&buffer))
var amount=UInt32(count)
checked(AudioFileReadBytes(file!,false,0,&amount,buffer!.pointee.mAudioData))
buffer!.pointee.mAudioDataByteSize=amount
checked(AudioQueueEnqueueBuffer(queue!,buffer!,0,nil))
checked(AudioQueueStart(queue!,nil))
Thread.sleep(forTimeInterval:Double(amount)/Double(format.mBytesPerFrame)/format.mSampleRate+0.3)
checked(AudioQueueStop(queue!,true));checked(AudioQueueDispose(queue!,true));checked(AudioFileClose(file!))
