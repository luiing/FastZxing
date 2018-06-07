package com.myapp.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.uis.fastzxing.OnCameraOpenListener;
import com.uis.fastzxing.OnScannerCompletionListener;
import com.uis.fastzxing.ScannerView;

public class ScanUi extends AppCompatActivity implements OnCameraOpenListener,OnScannerCompletionListener{
    ScannerView scan;
    String CAMERA = Manifest.permission.CAMERA;
    boolean isRequest = false;
    final int REQ_CAMERA = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_scan);
        scan = getView(R.id.scannerview);
        scan.setOnCameraOpenListener(this);
        scan.setOnScannerCompletionListener(this);
        scan.setDrawText("", true);
        scan.setDrawTextColor(Color.WHITE);
        scan.setLaserColor(getResources().getColor(R.color.red));
        scan.setLaserFrameBoundColor(getResources().getColor(R.color.red));
        //scan.setLaserFrameSize(300,300);

        if(PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this,CAMERA)){
            startScan();
        }else {
            ActivityCompat.requestPermissions(this,new String[]{CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onCameraOpen(boolean isOpen) {
        if(!isOpen){
            if(isRequest) {
                Log.e("scan", "deny camera...");
            }else{
                ActivityCompat.requestPermissions(this,new String[]{CAMERA}, REQ_CAMERA);
            }
        }
    }

    @Override
    public void OnScannerCompletion(Result rawResult, ParsedResult parsedResult, Bitmap barcode) {
        Log.e("scan","value = "+rawResult.getText());
        Intent res = new Intent();
        res.putExtra("data",String.valueOf(rawResult.getText()));
        setResult(Activity.RESULT_OK,res);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scan.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scan.onPause();
    }

    private void startScan(){
        scan.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(REQ_CAMERA == requestCode){
            isRequest = true;
            if(grantResults.length>0 && PackageManager.PERMISSION_GRANTED == grantResults[0]){
                startScan();
            }else {
                Log.e("scan", "deny camera...");
            }
        }else{

        }
    }

    private <V extends View> V getView(int id){
        return (V)findViewById(id);
    }
}
