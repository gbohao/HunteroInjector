package com.huntero.inject;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.huntero.injector.annotations.permission.ApplyPermissions;
import com.huntero.injector.annotations.permission.OnDenied;
import com.huntero.injector.annotations.permission.OnNeverAskAgain;
import com.huntero.injector.annotations.permission.OnShowRationale;
import com.huntero.injector.annotations.permission.PermissionRequest;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainActivity$PermissionAdapter.phoneWithCheck(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivity$PermissionAdapter.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @ApplyPermissions({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void takePhoto(){

    }
    @OnShowRationale({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void showRationale(final PermissionRequest request){

    }

//    @OnDenied({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void denied() {

    }
//    @OnNeverAskAgain({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void neverAskAgain() {

    }

//    //  ---  2  ---
    @ApplyPermissions({Manifest.permission.CALL_PHONE})
    public void phone() {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:111111"));
        startActivity(intent);
    }
    @OnShowRationale({Manifest.permission.CALL_PHONE})
    public void showRationale4Phone(final PermissionRequest callback){
        new AlertDialog.Builder(this).setTitle("权限请求").setPositiveButton("允许", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                callback.proceed();
            }
        }).setNegativeButton("拒绝", null).create().show();
    }

    @OnDenied({Manifest.permission.CALL_PHONE})
    public void denied4Phone() {

    }
    @OnNeverAskAgain({Manifest.permission.CALL_PHONE})
    public void neverAskAgain4Phone() {

    }
}
