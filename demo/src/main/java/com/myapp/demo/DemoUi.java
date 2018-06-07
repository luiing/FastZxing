package com.myapp.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DemoUi extends AppCompatActivity implements View.OnClickListener{

    Button btScan;
    TextView tvTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ui_demo);
        btScan = getView(R.id.bt_scan);
        tvTxt = getView(R.id.tv_txt);

        btScan.setOnClickListener(this);
    }

    private <V extends View> V getView(int id){
        return (V)findViewById(id);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(R.id.bt_scan == id){
            startApp(ScanUi.class,100);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        tvTxt.setText("req="+requestCode+",res="+resultCode+",result="+ (data != null ? data.getStringExtra("data"):"null") );
    }

    private void startApp(Class cls,int requestCode){
        Intent it = new Intent(this,cls);
        startActivityForResult(it,requestCode);
    }
}
