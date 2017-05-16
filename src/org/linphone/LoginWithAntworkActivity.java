package org.linphone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.File;

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
    private ProgressDialog dialog;

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
        clearCookies(getApplicationContext());
        try {
            trimCache(getApplicationContext());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        dialog = new ProgressDialog(LoginWithAntworkActivity.this);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    @SuppressWarnings("deprecation")
    public static void clearCookies(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(context);
            cookieSyncMngr.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }
    }

    /**
     * Prepares web view and its JavaScript interface
     */
    private void prepareWebView() {
        WebView webView = (WebView) findViewById(R.id.activity_main_login);

        webView.clearCache(true);
        webView.clearFormData();
        webView.clearHistory();
        webView.clearMatches();
        webView.clearSslPreferences();
        WebStorage.getInstance().deleteAllData();

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setAppCacheEnabled(false);
        webView.getSettings().setDatabaseEnabled(false);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                dialog.show();
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                dialog.dismiss();
            }
        });

        webView.loadUrl("https://members.antwork.com/#/account/");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface           // For API 17+
            public void callbackHandler(String value) {
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
                    SharedPreferences.Editor editor = getSharedPreferences(getPackageName(), MODE_PRIVATE).edit();
                    editor.putBoolean("IsAllowed", isAllowed).commit();
                    editor.putString("AntworkId", object.getJSONObject("Data").getString("UserID")).commit();
                    editor.putString("SIPUsername", object.getJSONObject("Data").getJSONObject("CustomProperties").getString("PBXExtension"));
                    editor.putString("SIPPassword", object.getJSONObject("Data").getJSONObject("CustomProperties").getString("PBXPassword"));
                    editor.apply();
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


    public static void trimCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    private class AsyncRequest extends AsyncTask<String, String, Boolean> {

        private String fullName = "";
        private String UserId = "";
        private String SIPUsername = "";
        private String SIPPassword = "";
        private ProgressDialog mDialog;

        public AsyncRequest() {
            if (mDialog == null) {
                mDialog = new ProgressDialog(LoginWithAntworkActivity.this);
                mDialog.setCancelable(false);
                mDialog.setCanceledOnTouchOutside(false);
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                OkHttpClient client = new OkHttpClient();

                RequestBody body = new FormBody.Builder().add("TOKEN", params[0]).add("DEVICETOKEN", "ANDROID").build();

                Request request = new Request.Builder()
                        .url("https://members.antwork.com/gateway/api/Users/GetUserDetails")
                        .post(body).build();
                Response response = client.newCall(request).execute();
                String jsonResponse = response.body().string();
                Log.d("Resp", jsonResponse);
                JSONObject jsonObject = new JSONObject(jsonResponse);
                fullName = jsonObject.getJSONObject("Data").getString("DisplayName");
                if (jsonObject.has("Data")) {
                    UserId = jsonObject.getJSONObject("Data").getString("UserID");
                    SIPUsername = jsonObject.getJSONObject("Data").getJSONObject("CustomProperties").getString("PBXExtension");
                    SIPPassword = jsonObject.getJSONObject("Data").getJSONObject("CustomProperties").getString("PBXPassword");
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
            mDialog.dismiss();

            if (SIPUsername == null || SIPUsername.equals("null") || SIPUsername.isEmpty() || SIPPassword.trim().length() == 0) {
                new AlertDialog.Builder(LoginWithAntworkActivity.this).setTitle(getString(R.string.error))
                        .setMessage(getString(R.string.error_no_valid_extension))
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        })
                        .show();
                return;
            }
            getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putBoolean("IsAllowed", s).commit();
            if (s) {
                SharedPreferences.Editor editor = getSharedPreferences(getPackageName(), MODE_PRIVATE).edit();
                editor.putBoolean("IsAllowed", s).commit();
                editor.putString("AntworkId", UserId).commit();
                editor.putString("SIPUsername", SIPUsername);
                editor.putString("SIPPassword", SIPPassword);
                editor.apply();
                Intent intt = new Intent(LoginWithAntworkActivity.this, LinphoneActivity.class);
                if (data != null) {
                    intt.setData(data);
                }
                startActivity(intt);
                finish();
            } else {
                new AlertDialog.Builder(LoginWithAntworkActivity.this).setTitle(getString(R.string.error))
                        .setMessage(getString(R.string.error_bad_credentials))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
            }
        }
    }
}
