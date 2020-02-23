/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.jeremyup.cordova.x5engine;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.PermissionRequest;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.GeolocationPermissionsCallback;
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebStorage;
import com.tencent.smtt.sdk.WebView;

import org.apache.cordova.CordovaDialogsHelper;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.camera.FileProvider;

import java.io.File;
import java.util.Arrays;

/**
 * This class is the WebChromeClient that implements callbacks for our web view.
 * The kind of callbacks that happen here are on the chrome outside the document,
 * such as onCreateWindow(), onConsoleMessage(), onProgressChanged(), etc. Related
 * to but different than CordovaWebViewClient.
 * <p>
 * Created by jeremy on 2017/5/18.
 */
public class X5WebChromeClient extends WebChromeClient {

    private static final int FILECHOOSER_RESULTCODE = 5173;
    private static final String LOG_TAG = "X5WebChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;
    protected final X5WebViewEngine parentEngine;
    //xgp add
    private static final String TAG = "UserInfoActivity";
    private static final int REQUEST_IMAGE_GET = 0;
    private static final int REQUEST_IMAGE_CAPTURE = 1555;
    private static final int REQUEST_SMALL_IMAGE_CUTTING = 2;
    private static final int REQUEST_CHANGE_USER_NICK_NAME = 10;
    private static final String IMAGE_FILE_NAME = "user_head_icon.jpg";


    // the video progress view
    private View mVideoProgressView;

    private CordovaDialogsHelper dialogsHelper;
    private Context appContext;

    private IX5WebChromeClient.CustomViewCallback mCustomViewCallback;
    private View mCustomView;

    public X5WebChromeClient(X5WebViewEngine parentEngine) {
        this.parentEngine = parentEngine;
        appContext = parentEngine.webView.getContext();
        dialogsHelper = new CordovaDialogsHelper(appContext);
    }

    /**
     * Tell the client to display a javascript alert dialog.
     */
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        dialogsHelper.showAlert(message, new CordovaDialogsHelper.Result() {
            @Override
            public void gotResult(boolean success, String value) {
                if (success) {
                    result.confirm();
                } else {
                    result.cancel();
                }
            }
        });
        return true;
    }

    /**
     * Tell the client to display a confirm dialog to the user.
     */
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        dialogsHelper.showConfirm(message, new CordovaDialogsHelper.Result() {
            @Override
            public void gotResult(boolean success, String value) {
                if (success) {
                    result.confirm();
                } else {
                    result.cancel();
                }
            }
        });
        return true;
    }

    /**
     * Tell the client to display a prompt dialog to the user.
     * If the client returns true, WebView will assume that the client will
     * handle the prompt dialog and call the appropriate JsPromptResult method.
     * <p>
     * Since we are hacking prompts for our own purposes, we should not be using them for
     * this purpose, perhaps we should hack console.log to do this instead!
     */
    public boolean onJsPrompt(WebView view, String origin, String message, String defaultValue, final JsPromptResult result) {
        // Unlike the @JavascriptInterface bridge, this method is always called on the UI thread.
        String handledRet = parentEngine.bridge.promptOnJsPrompt(origin, message, defaultValue);
        if (handledRet != null) {
            result.confirm(handledRet);
        } else {
            dialogsHelper.showPrompt(message, defaultValue, new CordovaDialogsHelper.Result() {
                @Override
                public void gotResult(boolean success, String value) {
                    if (success) {
                        result.confirm(value);
                    } else {
                        result.cancel();
                    }
                }
            });
        }
        return true;
    }

    /**
     * Handle database quota exceeded notification.
     */
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
                                        long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
        LOG.d(LOG_TAG, "onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d", estimatedSize, currentQuota, totalUsedQuota);
        quotaUpdater.updateQuota(MAX_QUOTA);
    }

    @TargetApi(8)
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (consoleMessage.message() != null)
            LOG.d(LOG_TAG, "%s: Line %d : %s", consoleMessage.sourceId(), consoleMessage.lineNumber(), consoleMessage.message());
        return super.onConsoleMessage(consoleMessage);
    }

    /**
     * Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
     * <p>
     * This also checks for the Geolocation Plugin and requests permission from the application  to use Geolocation.
     *
     * @param origin
     * @param callback
     */
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissionsCallback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
        //Get the plugin, it should be loaded
        CordovaPlugin geolocation = parentEngine.pluginManager.getPlugin("Geolocation");
        if (geolocation != null && !geolocation.hasPermisssion()) {
            geolocation.requestPermissions(0);
        }

    }

    // API level 7 is required for this, see if we could lower this using something else
    public void onShowCustomView(View view, final IX5WebChromeClient.CustomViewCallback callback) {
        // IX5WebChromeClient.CustomViewCallback casts to webkit.WebChromeClient.CustomViewCallback
        // By Jeremy on 2017/5/18.
        parentEngine.getCordovaWebView().showCustomView(view, new android.webkit.WebChromeClient.CustomViewCallback() {
            @Override
            public void onCustomViewHidden() {
                callback.onCustomViewHidden();
            }
        });
    }

    public void onHideCustomView() {
        parentEngine.getCordovaWebView().hideCustomView();
    }

    /**
     * Ask the host application for a custom progress view to show while
     * a <video> is loading.
     *
     * @return View The progress view.
     */
    public View getVideoLoadingProgressView() {

        if (mVideoProgressView == null) {
            // Create a new Loading view programmatically.

            // create the linear layout
            LinearLayout layout = new LinearLayout(parentEngine.getView().getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            layout.setLayoutParams(layoutParams);
            // the proress bar
            ProgressBar bar = new ProgressBar(parentEngine.getView().getContext());
            LinearLayout.LayoutParams barLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            barLayoutParams.gravity = Gravity.CENTER;
            bar.setLayoutParams(barLayoutParams);
            layout.addView(bar);

            mVideoProgressView = layout;
        }
        return mVideoProgressView;
    }

    // <input type=file> support:
    // openFileChooser() is for pre KitKat and in KitKat mr1 (it's known broken in KitKat).
    // For Lollipop, we use onShowFileChooser().
    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        this.openFileChooser(uploadMsg, "*/*");
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        this.openFileChooser(uploadMsg, acceptType, null);
    }

    public void openFileChooser(final ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
//        if(true){
//            imageCapture();
//            return ;
//        }
        Log.d(TAG, "onShowFileChooser: 是否拍照？？ " + capture);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        parentEngine.cordova.startActivityForResult(new CordovaPlugin() {
            @Override
            public void onActivityResult(int requestCode, int resultCode, Intent intent) {
                Uri result = intent == null || resultCode != Activity.RESULT_OK ? null : intent.getData();
                LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                uploadMsg.onReceiveValue(result);
            }
        }, intent, FILECHOOSER_RESULTCODE);
    }

    //iOS最遵守遵守HTML5规范，其次是X5内核，安卓的webview基本忽略了capture。
    //理想情况下应该按照如下开发webview：
//1. 当accept=”image/*”时，capture=”user”调用前置照相机，capture=”其他值”，调用后置照相机
//2. 当accept=”video/*”时，capture=”user”调用前置录像机，capture=”其他值”，调用后置录像机
//3. 当accept=”image/*,video/*”，capture=”user”调用前置摄像头，capture=”其他值”，调用后置摄像头，默认照相，可切换录像
//4. 当accept=”audio/*”时，capture=”放空或者任意值”，调用录音机
//5. 当input没有capture时，根据accppt类型给出文件夹选项以及摄像头或者录音机选项
//6. input含有multiple时访问文件夹可勾选多文件，调用系统摄像头或者录音机都只是单文件
//7. 无multiple时都只能单文件
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean onShowFileChooser(WebView webView, final ValueCallback<Uri[]> filePathsCallback, final FileChooserParams fileChooserParams) {
        Intent intent = fileChooserParams.createIntent();
        //@change
        String[] acceptTypes = fileChooserParams.getAcceptTypes();
        String type = "*/*";
        if (acceptTypes.length > 0) {
            type = acceptTypes[0];
        }
        if (fileChooserParams.isCaptureEnabled()) {
            imageCapture(filePathsCallback);
            return true;
        }


        final String[] items3 = new String[]{"文件选择", "拍照"};//创建item
        AlertDialog alertDialog3 = new AlertDialog.Builder(parentEngine.cordova.getContext())
                .setItems(items3, new DialogInterface.OnClickListener() {//添加列表
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 1) {
                            imageCapture(filePathsCallback);
                        } else {
                            try {
                                CordovaPlugin cordovaPlugin = new CordovaPlugin() {
                                    @Override
                                    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
//                    Uri[] result = FileChooserParams.parseResult(resultCode, intent);//@change 这里选择文件报错，改为下面的写法
                                        Uri[] result = android.webkit.WebChromeClient.FileChooserParams.parseResult(resultCode, intent);
                                        LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                                        filePathsCallback.onReceiveValue(result);
                                    }
                                };
                                parentEngine.cordova.startActivityForResult(cordovaPlugin, intent, FILECHOOSER_RESULTCODE);
                            } catch (ActivityNotFoundException e) {
                                LOG.w("No activity found to handle file chooser intent.", e);
                                filePathsCallback.onReceiveValue(null);
                            }
                        }
                    }
                }).create();
        alertDialog3.show();

        //@change over


        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void Request() {
        Context context = parentEngine.cordova.getActivity().getApplicationContext();
        //获取相机拍摄读写权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //版本判断
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(parentEngine.cordova.getActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
            }
        }
    }

    private void imageCapture(ValueCallback<Uri[]> filePathsCallback) {
        PictureUtil.mkdirMyPetRootDirectory();
        Uri pictureUri = null;
        //getMyPetRootDirectory()得到的是Environment.getExternalStorageDirectory() + File.separator+"MyPet"
        //也就是我之前创建的存放头像的文件夹（目录）
        File pictureFile = new File(PictureUtil.getMyPetRootDirectory(), System.currentTimeMillis() + ".jpg");
        Intent intent = null;
        // 判断当前系统
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Request();
            intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            //这一句非常重要
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String packname = parentEngine.cordova.getActivity().getPackageName();
            //""中的内容是随意的，但最好用package名.provider名的形式，清晰明了
            Context context = parentEngine.cordova.getContext();
            pictureUri = FileProvider.getUriForFile(context,
                    packname + ".provider", pictureFile);
        } else {
            intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            pictureUri = Uri.fromFile(pictureFile);
        }
        // 去拍照,拍照的结果存到pictureUri对应的路径中
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pictureUri);
        Log.e(TAG, "before take photo" + pictureUri.toString());
        Uri finalPictureUri = pictureUri;
        CordovaPlugin cordovaPlugin = new CordovaPlugin() {
            @Override
            public void onActivityResult(int requestCode, int resultCode, Intent intent) {
//                    Uri[] result = FileChooserParams.parseResult(resultCode, intent);
                if (pictureFile.exists()) {
                    int angle = PictureUtil.readPictureDegree(pictureFile.getPath());
                    if (angle != 0) {//部分手机会旋转图片，这里给旋转回来
                        Log.e("TAG", "degree====" + angle);
                        Bitmap bitmapori = BitmapFactory.decodeFile(pictureFile.getPath());
                        // 修复图片被旋转的角度
                        Bitmap bitmap = PictureUtil.rotaingImageView(angle, bitmapori);
                        PictureUtil.saveBitmap(bitmap, pictureFile);
                    }
                    Uri[] result = new Uri[]{finalPictureUri};
                    LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                    filePathsCallback.onReceiveValue(result);
                } else {
                    Log.d(TAG, "onActivityResult: 拍照失败！！！");
                }
            }
        };
        parentEngine.cordova.startActivityForResult(cordovaPlugin, intent, REQUEST_IMAGE_CAPTURE);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onPermissionRequest(final PermissionRequest request) {
        LOG.d(LOG_TAG, "onPermissionRequest: " + Arrays.toString(request.getResources()));
        request.grant(request.getResources());
    }

    public void destroyLastDialog() {
        dialogsHelper.destroyLastDialog();
    }
}
