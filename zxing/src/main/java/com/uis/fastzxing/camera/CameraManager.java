/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uis.fastzxing.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.uis.fastzxing.camera.open.OpenCamera;
import com.uis.fastzxing.camera.open.OpenCameraInterface;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();

  private static final int MIN_FRAME_WIDTH = 240;//默认扫码框为屏幕5/8
  private static final int MIN_FRAME_HEIGHT = 240;
  public static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920=1200
  public static final int MAX_FRAME_HEIGHT = 675; // = 5/8 * 1080=675

  private final Context context;
  private final CameraConfigurationManager configManager;
  private OpenCamera camera;
  private AutoFocusManager autoFocusManager;
  private Rect framingRect;
  private Rect framingRectPreview;
  private boolean initialized;
  private boolean previewing;
  private int requestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
  private int frameRectWidth,frameRectHeight;
  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private final PreviewCallback previewCallback;
  private int laserFrameTopMargin = 0;//扫描框离屏幕上方距离

  public CameraManager(Context context) {
    this.context = context;
    this.configManager = new CameraConfigurationManager(context);
    previewCallback = new PreviewCallback(configManager);
  }

  public Context getContext() {
    return context;
  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public synchronized void openDriver(SurfaceHolder holder) throws IOException {
    OpenCamera theCamera = camera;
    if (theCamera == null) {
      //获取手机背面的摄像头
      theCamera = OpenCameraInterface.open(requestedCameraId);
      if (theCamera == null) {
        throw new IOException("Camera.open() failed to return object from driver");
      }
      camera = theCamera;
    }

    if (!initialized) {
      initialized = true;
      configManager.initFromCameraParameters(theCamera);
      if (frameRectWidth > 0 && frameRectHeight > 0) {
        setManualFramingRect(frameRectWidth, frameRectHeight);
      }
    }

    Camera cameraObject = theCamera.getCamera();
    Camera.Parameters parameters = cameraObject.getParameters();
    String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save these, temporarily
    try {
      configManager.setDesiredCameraParameters(theCamera, false);
    } catch (RuntimeException re) {
      if (parametersFlattened != null) {
        parameters = cameraObject.getParameters();
        parameters.unflatten(parametersFlattened);
        try {
          cameraObject.setParameters(parameters);
          configManager.setDesiredCameraParameters(theCamera, true);
        } catch (RuntimeException re2) {
          Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
        }
      }
    }
    cameraObject.setPreviewDisplay(holder);

  }

  public synchronized boolean isOpen() {
    return camera != null;
  }

  /**
   * Closes the camera driver if still in use.
   */
  public synchronized void closeDriver() {
    if (camera != null) {
      camera.getCamera().release();
      camera = null;
      framingRect = null;
      framingRectPreview = null;
    }
  }

  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  public synchronized void startPreview() {
    OpenCamera theCamera = camera;
    if (theCamera != null && !previewing) {
      theCamera.getCamera().startPreview();
      previewing = true;
      autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  public synchronized void stopPreview() {
    if (autoFocusManager != null) {
      autoFocusManager.stop();
      autoFocusManager = null;
    }
    if (camera != null && previewing) {
      camera.getCamera().stopPreview();
      previewCallback.setHandler(null, 0);
      previewing = false;
    }
  }

  /**
   * Convenience method for
   *
   * @param newSetting if {@code true}, light should be turned on if currently off. And vice versa.
   *
   */
  public synchronized void setTorch(boolean newSetting) {
    OpenCamera theCamera = camera;
    if (theCamera != null) {
      if (newSetting != configManager.getTorchState(theCamera.getCamera())) {
        boolean wasAutoFocusManager = autoFocusManager != null;
        if (wasAutoFocusManager) {
          autoFocusManager.stop();
          autoFocusManager = null;
        }
        configManager.setTorch(theCamera.getCamera(), newSetting);
        if (wasAutoFocusManager) {
          autoFocusManager = new AutoFocusManager(context, theCamera.getCamera());
          autoFocusManager.start();
        }
      }
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
   * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
   * respectively.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public synchronized void requestPreviewFrame(Handler handler, int message) {
    OpenCamera theCamera = camera;
    if (theCamera != null && previewing) {
      previewCallback.setHandler(handler, message);
      theCamera.getCamera().setOneShotPreviewCallback(previewCallback);
    }
  }

  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device
   * far enough away to ensure the image will be in focus.
   * 获取扫描框大小
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  public synchronized Rect getFramingRect() {
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }
      Point screenResolution = configManager.getScreenResolution();
      if (screenResolution == null) {
        return null;
      }
      int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
      int height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);
      //竖屏则为正方形
      if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
        height = width;
      }
      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height ) / 2 ;
      topOffset = topOffset + laserFrameTopMargin;
      framingRect = new Rect(leftOffset, topOffset,leftOffset + width,topOffset + height);
    }
    return framingRect;
  }
  
  private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
    int dim = resolution*5/8; // Target 5/8 of each dimension
    if (dim < hardMin) {
      return hardMin;
    }
    if (dim > hardMax) {
      return hardMax;
    }
    return dim;
  }

  /**
   * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
   * not UI / screen.
   * 获取预览图片大小
   * @return {@link Rect} expressing barcode scan area in terms of the preview size
   */
  public synchronized Rect getFramingRectInPreview() {
    if(framingRectPreview == null && framingRect != null){
      Point cameraP = configManager.getCameraResolution();
      Point screenP = configManager.getScreenResolution();
      if (cameraP == null || screenP == null) {
        return null;
      }
      Rect rect = new Rect(framingRect);
      int width = rect.left + rect.right;
      int height = rect.top + rect.bottom;
      int extend = 0;
      float ratio;
      if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
        //竖屏 cameraP=Point(1920, 1080),screenP=Point(1080, 1920)
        if(rect.left > width/12){
          extend = rect.left - width/12;
        }
        ratio = Math.min(1.0f*cameraP.x/screenP.y,1.0f*cameraP.y/screenP.x);
      } else {//横屏 cameraP=Point(1920, 1080),screenP=Point(1920, 1080)
        if(rect.top > height/12){
          extend = rect.top - width/12;
        }
        ratio = Math.min(1.0f*cameraP.x/screenP.x,1.0f*cameraP.y/screenP.y);
      }
      if(extend > 0){
        rect.left -= extend;
        rect.right += extend;
        rect.top -= extend;
        rect.bottom += extend;
      }
      rect.left *= ratio;
      rect.right *= ratio;
      rect.top *= ratio;
      rect.bottom *= ratio;
      framingRectPreview = rect;
    }
    return framingRectPreview;
  }

  
  /**
   * Allows third party apps to specify the camera ID, rather than determine
   * it automatically based on available cameras and their orientation.
   *
   * @param cameraId camera ID of the camera to use. A negative value means "no preference".
   */
  public synchronized void setManualCameraId(int cameraId) {
    requestedCameraId = cameraId;
  }
  
  /**
   * Allows third party apps to specify the scanning rectangle dimensions, rather than determine
   * them automatically based on screen resolution.
   * 设置扫描框大小
   * @param width The width in pixels to scan.
   * @param height The height in pixels to scan.
   */
  public synchronized void setManualFramingRect(int width, int height) {
    if(!initialized){
      frameRectWidth = width;
      frameRectHeight = height;
      return;
    }
    Point screenResolution = configManager.getScreenResolution();
    if (width > screenResolution.x) {
      width = screenResolution.x;
    }
    if (height > screenResolution.y) {
      height = screenResolution.y;
    }
    int leftOffset = (screenResolution.x - width) / 2;
    int topOffset = (screenResolution.y - height) / 2;
    topOffset += laserFrameTopMargin;
    framingRect = new Rect(leftOffset, topOffset, leftOffset + width,topOffset + height);
    framingRectPreview = null;
  }

  /**
   * A factory method to build the appropriate LuminanceSource object based on the format
   * of the preview buffers, as described by Camera.Parameters.
   *
   * @param data A preview frame.
   * @param width The width of the image.
   * @param height The height of the image.
   * @return A PlanarYUVLuminanceSource instance.
   */
  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    if (rect == null) {
      return null;
    }
    //Log.e("camera","width="+width+",height="+height+ ",crop="+rect);
    PlanarYUVLuminanceSource source = null;
    try{
      source = new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
              rect.width(), rect.height(), false);
    }catch (Exception ex){
      ex.printStackTrace();
    }
    return source;
  }

  /**
   * 设置扫描框与屏幕上方距离
   * @param laserFrameTopMargin
   */
  public void setLaserFrameTopMargin(int laserFrameTopMargin) {
    this.laserFrameTopMargin = laserFrameTopMargin;
  }
}
