package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import com.myapp.demo.R;

public class DemoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

    }

    private <V extends View> V getView(int id){
        return (V)findViewById(id);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e("demo","req="+requestCode+",res="+resultCode);

    }

    private void startApp(String name,Bundle bd,int requestCode){
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setClassName("com.bg.bgpay","com.bl.mobilepay.ui."+name);
        if(bd!=null){
            it.putExtras(bd);
        }
        startActivityForResult(it,requestCode);
    }
}
