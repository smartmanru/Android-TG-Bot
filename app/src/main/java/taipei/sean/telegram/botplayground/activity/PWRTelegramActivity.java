package taipei.sean.telegram.botplayground.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import taipei.sean.telegram.botplayground.InstantComplete;
import taipei.sean.telegram.botplayground.PWRTelegramAPI;
import taipei.sean.telegram.botplayground.R;
import taipei.sean.telegram.botplayground.SeanAdapter;
import taipei.sean.telegram.botplayground.SeanDBHelper;
import taipei.sean.telegram.botplayground.adapter.ApiCallerAdapter;

public class PWRTelegramActivity extends AppCompatActivity {
    final private int _dbVer = 4;
    private SeanDBHelper db;
    private String _token;
    private int _type;
    private PWRTelegramAPI _api;
    private JSONObject apiMethods;
    private JSONObject pApiMethods;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwrtelegram);

        ActionBar actionBar = getSupportActionBar();
        if (null != actionBar)
            actionBar.setDisplayHomeAsUpEnabled(true);


        try {
            Bundle bundle = getIntent().getExtras();
            _token = bundle.getString("token");
            _type = bundle.getInt("type");
        } catch (NullPointerException e) {
            Log.e("caller", "bundle error", e);
            finish();
        }

        if (_type == 2) {
            Intent mIntent = new Intent(PWRTelegramActivity.this, MadelineActivity.class);
            mIntent.putExtra("token", _token);
            mIntent.putExtra("type", _type);
            startActivity(mIntent);
            finish();
        }

        db = new SeanDBHelper(this, "data.db", null, _dbVer);

        _api = new PWRTelegramAPI(this, _token, _type);

        final InstantComplete methodView = (InstantComplete) findViewById(R.id.pwrtelegram_method);
        final Button submitButton = (Button) findViewById(R.id.pwrtelegram_submit);


        final ArrayList<String> botApiMethodsList = new ArrayList<String>() {};
        pApiMethods = loadPMethods();
        apiMethods = loadMethods();


        Iterator<String> pTemp = pApiMethods.keys();
        while (pTemp.hasNext()) {
            String key = pTemp.next();
            botApiMethodsList.add(key);
        }

        Iterator<String> temp = apiMethods.keys();
        while (temp.hasNext()) {
            String key = temp.next();
            botApiMethodsList.add(key);
        }

        final SeanAdapter<String> adapter = new SeanAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, botApiMethodsList);
        methodView.setAdapter(adapter);

        methodView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                updateMethod();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submit();
            }
        });

        String method = db.getParam("_method_pwrt");
        if (pApiMethods.has(method) || apiMethods.has(method))
            methodView.setText(method);
        updateMethod();
    }

    private void updateMethod() {
        final InstantComplete methodView = (InstantComplete) findViewById(R.id.pwrtelegram_method);
        final RecyclerView paramList = (RecyclerView) findViewById(R.id.pwrtelegram_inputs);
        final String method = methodView.getText().toString();

        JSONObject paramData;

        try {
            JSONObject methodData;

            if (pApiMethods.has(method)) {
                methodData = pApiMethods.getJSONObject(method);
            } else if (apiMethods.has(method)) {
                methodData = apiMethods.getJSONObject(method);
            } else {
                methodView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                ViewGroup.LayoutParams layoutParams = paramList.getLayoutParams();
                layoutParams.height = 0;
                paramList.setLayoutParams(layoutParams);
                return;
            }

            if (methodData.has("params"))
                paramData = methodData.getJSONObject("params");
            else {
                Log.d("caller", "No params: " + method);
                return;
            }
        } catch (JSONException e) {
            Log.e("caller", "json", e);
            return;
        }

        ApiCallerAdapter apiCallerAdapter = new ApiCallerAdapter(getApplicationContext());

        try {
            Iterator<String> temp = paramData.keys();
            while (temp.hasNext()) {
                String key = temp.next();
                JSONObject value = paramData.getJSONObject(key);
                apiCallerAdapter.addData(key, value);
            }
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
        }

        paramList.setAdapter(apiCallerAdapter);
        paramList.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        paramList.setItemViewCacheSize(paramData.length());

        db.updateParam("_method_pwrt", method);
        apiCallerAdapter.fitView(paramList);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pwrtelegram, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.pwrt_menu_madeline:
                Intent mIntent = new Intent(PWRTelegramActivity.this, MadelineActivity.class);
                mIntent.putExtra("token", _token);
                mIntent.putExtra("type", _type);
                startActivity(mIntent);
                break;
            case R.id.pwrt_menu_info:
                Uri infoUri = Uri.parse("https://t.me/PWRTelegram");
                Intent iIntent = new Intent(Intent.ACTION_VIEW, infoUri);
                startActivity(iIntent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void submit() {
        final InstantComplete methodView = (InstantComplete) findViewById(R.id.pwrtelegram_method);
        final RecyclerView paramList = (RecyclerView) findViewById(R.id.pwrtelegram_inputs);
        final TextView resultView = (TextView) findViewById(R.id.pwrtelegram_result);

        String method = methodView.getText().toString();

        JSONObject jsonObject = new JSONObject();

        final ApiCallerAdapter paramAdapter = (ApiCallerAdapter) paramList.getAdapter();
        final int paramHeight = paramList.getHeight();
        if (null == paramAdapter || paramHeight == 0) {
            _api.callApi(method, resultView, jsonObject);
            return;
        }

        final int inputCount = paramAdapter.getItemCount();
        for (int i=0; i<inputCount; i++) {
            TextInputLayout textInputLayout = (TextInputLayout) paramAdapter.getViewByPos(i);
            InstantComplete textInputEditText = (InstantComplete) textInputLayout.getEditText();
            if (null == textInputEditText) {
                Log.w("caller", "edit text null");
                continue;
            }
            CharSequence hint = textInputLayout.getHint();
            if (null == hint) {
                Log.w("caller", "hint null");
                continue;
            }
            String name = hint.toString();
            CharSequence valueChar = textInputEditText.getText();
            if (null == valueChar) {
                Log.w("caller", "value char null");
                continue;
            }
            String value = valueChar.toString();

            if (Objects.equals(value, "")) {
                Log.w("caller", "value empty");
                continue;
            }

            try {
                jsonObject.put(name, value);
                db.insertFav(name, value, method);
            } catch (JSONException e) {
                Log.e("caller", "json", e);
            }
        }

        _api.callApi(method, resultView, jsonObject);
    }

    public JSONObject loadMethods() {
        String jsonStr;
        JSONObject json;
        try {
            InputStream is = getAssets().open("api-methods.json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            jsonStr = new String(buffer, "UTF-8");
        } catch (IOException e) {
            Log.e("caller", "get", e);
            return null;
        }
        try {
            json = new JSONObject(jsonStr);
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
            return null;
        }
        return json;
    }

    public JSONObject loadPMethods() {
        String jsonStr;
        JSONObject json;
        try {
            InputStream is = getAssets().open("pwrtelegram-methods.json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            jsonStr = new String(buffer, "UTF-8");
        } catch (IOException e) {
            Log.e("caller", "get", e);
            return null;
        }
        try {
            json = new JSONObject(jsonStr);
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
            return null;
        }
        return json;
    }
}
