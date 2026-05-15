// Objective-C bridge — required by React Native to register view managers.

#import <React/RCTViewManager.h>
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(RNSceneViewManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(environment, NSString)
RCT_EXPORT_VIEW_PROPERTY(modelNodes, NSArray)
RCT_EXPORT_VIEW_PROPERTY(cameraOrbit, BOOL)
// v4.3.0 camera + content APIs (issue #1053)
RCT_EXPORT_VIEW_PROPERTY(cameraControlMode, NSString)
RCT_EXPORT_VIEW_PROPERTY(autoCenterContent, BOOL)
RCT_EXPORT_VIEW_PROPERTY(onTap, RCTDirectEventBlock)

@end

@interface RCT_EXTERN_MODULE(RNARSceneViewManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(environment, NSString)
RCT_EXPORT_VIEW_PROPERTY(modelNodes, NSArray)
RCT_EXPORT_VIEW_PROPERTY(planeDetection, BOOL)
RCT_EXPORT_VIEW_PROPERTY(depthOcclusion, BOOL)
RCT_EXPORT_VIEW_PROPERTY(instantPlacement, BOOL)
RCT_EXPORT_VIEW_PROPERTY(onTap, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onPlaneDetected, RCTDirectEventBlock)

@end

// v4.3.0 AR recording native module (issue #1053). ReplayKit-backed,
// iOS-only — the JS `ARRecorder` class gates non-iOS platforms.
@interface RCT_EXTERN_MODULE(RNARRecorder, NSObject)

RCT_EXTERN_METHOD(start:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(stop:(NSString *)outputPath
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(saveToPhotoLibrary:(NSString *)movPath
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
