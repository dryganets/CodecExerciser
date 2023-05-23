# CodecExerciser
Sharing with Google steps to repro issues in the media codecs when codec reuse is enabled in exoplayer.

This simple app designed to reproduce codec issues in the ExoPlayer in codec reuse mode.
We found that usage of this mode or usage of DummySurfaceView alone could make playback much less resilient.

To find more about codec reuse please see this [blog article](https://medium.com/google-exoplayer/improved-decoder-reuse-in-exoplayer-ef4c6d99591d)

## Pre-requirements
- Java 17 is required to run the latest version of gradle.
- export JAVA_HOME=$(/usr/libexec/java_home -v17)
- ANDROID sdk installed API-level 33 loaded

## How to use this app
You could install it and try to use various modes, though it is likely to work as expected for you.
The most efficient way to repro the codec issues on the given device is to run the stress test.

There are two stress tests inside of the app
1. surfaceShufflingStressTest - create 6 surface views and directs the playback to different surface view every time
2. exoPlayerSimulationStressTest - uses the same instance of the ExoPlayer that constantly switches between different. It has an implementation that handles codec switch better than exoplayer's implementation but it uses slightly simplified approach.
3. startExoPlayerStressTest - uses exoPlayer's codec reuse.

## Problem in the Media stack discovered with this tool

Some of the Huawei devices with codec reuse enabled have a very high rate of the MEDIA_ERROR_RENDERER errors.
Majority of non Huawei device also have 10x increase in media codec errors.

In most of the cases Surface stop responding due to the internal platform error.

### Rootcause of the problem

```cpp
Surface::setBufferCount                             	- fails
setupNativeWindowSizeFormatAndUsage                  	- returns error 22
ACodec::handleSetSurface                             	- returns error 22
ACodec::onMessageReceived(AC::kWhatSetSurface)        - reply with error
ACodec::postAndWaitResponse(AC::kWhatSetSurface)	- returns an error
ACodec::setSurface						- returns an error


MediaCodec::onMessageReceived(MC::kWhatSetSurface)
{
    status_t err = connectToSurface
    if (err == OK) {
        // Here google overrides the error code of connectToSurface
        err = ACodec::setSurface
    }
    ...
    // In case setSurface failed we will never disconnect from the
    // surface and it will render it unusable
    if (err == OK) {
        disconnectFromSurface()
    }
    
}
MediaCodec::PostAndAwaitResponse(MC::kWhatSetSurface)
MediaCodec::SetSurface
```
Fortunatelly there is a timeout on the platform level, so Surface doesn't actually leak it just rendeded unusable for some time.

In the current code base [this line](https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/libstagefright/MediaCodec.cpp;l=4257;bpv=1;bpt=0) causes an issue. It overrides the error_code that has been returned in [this line](https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/libstagefright/MediaCodec.cpp;l=4242;bpv=1;bpt=0).

As a result despite the connect method has returned OK value - the disconnect never will be called due to the setSurface failure.


