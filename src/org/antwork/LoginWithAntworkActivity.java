package org.antwork;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginWithAntworkActivity extends Activity {
    public static final MediaType URLEncoded
            = MediaType.parse("text/plain");
    private Uri data = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_login_with_antwork);
        this.prepareWebView();
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        data = getIntent().getData();
    }

    /**
     * Prepares web view and its JavaScript interface
     */
    private void prepareWebView() {
        WebView webView = (WebView) findViewById(R.id.activity_main_login);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.loadUrl("https://members.antwork.com/#/account/externalsignin?appId=NAAPP&appDomain=http://localhost/&devicePlatform=ANDROID&deviceToken=NAAPPbccb1599a022b97fcc95b932155e1d6f&openLocation=http://localhost/ninja/public/login");
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface           // For API 17+
            public void callbackHandler(String value) {
                Log.d("callBackHandler", value);
                parseJSON(value);
            }

            @JavascriptInterface           // For API 17+
            public void callNativeApp(Object value) {
                // String stringVariable = value;
                // Log.d("callNativeApp", value);
                // Toast.makeText(LoginWithAntworkActivity.this, stringVariable, Toast.LENGTH_SHORT).show();
            }
        }, "ok");
    }

    /**
     * Parse response from server
     */
    private void parseJSON(String jsonResult) {
        try {
            JSONObject object = new JSONObject(jsonResult);
            if (object.has("Data")) {
                boolean isAllowed = object.getJSONObject("Data").getBoolean("CanAccess");
                if (isAllowed) {
                    getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean("IsAllowed", isAllowed).commit();
                    getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString("AntworkId", object.getJSONObject("Data").getString("UserID")).commit();
                }
            } else if (object.has("authResponse")) {
                String userId = object.getJSONObject("authResponse").getString("userID");
                String token = object.getJSONObject("authResponse").getString("accessToken");
                new AsyncRequest().execute(token);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new AlertDialog.Builder(this).setTitle(getString(R.string.error))
                    .setMessage(ex.getLocalizedMessage())
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
        }
    }


    private class AsyncRequest extends AsyncTask<String, String, Boolean> {
        private ProgressDialog dialog;
        private String fullName = "";
        private String UserId = "";

        public AsyncRequest() {
            dialog = new ProgressDialog(LoginWithAntworkActivity.this);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            this.dialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                OkHttpClient client = new OkHttpClient();

                RequestBody body = new FormBody.Builder().add("TOKEN", params[0]).add("DEVICETOKEN", "NAAPPbccb1599a022b97fcc95b932155e1d6f").build();

                Request request = new Request.Builder()
                        .url("https://members.antwork.com/server/api/apiservice/GetUserDetails")
                        .addHeader("EXTERNALAPPID", "NAAPP")
                        .addHeader("EXTERNALAPPSECRET", "k659Tf4R8105hLw")
                        .post(body).build();
                Response response = client.newCall(request).execute();
                String jsonResponse = response.body().string();
                Log.d("Resp", jsonResponse);
                JSONObject jsonObject = new JSONObject(jsonResponse);
                fullName = jsonObject.getJSONObject("Data").getString("DisplayName");
                if (jsonObject.has("Data")) {
                    UserId = jsonObject.getJSONObject("Data").getString("UserID");
                    boolean isAllowed = jsonObject.getJSONObject("Data").getBoolean("CanAccess");
                    return isAllowed;

                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean s) {
            super.onPostExecute(s);
            dialog.dismiss();
            getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean("IsAllowed", s).commit();

            if (s) {
                getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString("AntworkId", UserId).commit();
                new AlertDialog.Builder(LoginWithAntworkActivity.this).setMessage("Welcome " + fullName).setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intt = new Intent(LoginWithAntworkActivity.this, LinphoneActivity.class);
                        if (data != null) {
                            intt.setData(data);
                        }
                        startActivity(intt);
                        finish();
                    }
                }).show();
            } else {
                new AlertDialog.Builder(LoginWithAntworkActivity.this).setTitle(getString(R.string.error))
                        .setMessage(getString(R.string.error_bad_credentials))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
            }
        }
    }
}
