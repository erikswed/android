package com.bitlove.fetlife.view.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bitlove.fetlife.BuildConfig;
import com.bitlove.fetlife.FetLifeApplication;
import com.bitlove.fetlife.R;
import com.bitlove.fetlife.event.LoginFailedEvent;
import com.bitlove.fetlife.event.LoginFinishedEvent;
import com.bitlove.fetlife.event.LoginStartedEvent;
import com.bitlove.fetlife.model.service.FetLifeApiIntentService;
import com.onesignal.OneSignal;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class LoginActivity extends Activity {

    private EditText mUserNameView;
    private EditText mPasswordView;
    private CheckBox passwordPreferenceCheckBox;
    private Button logonButton;
    private Button logonProgressFakeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        // Set up the login form.
        mUserNameView = (EditText) findViewById(R.id.username);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        passwordPreferenceCheckBox = (CheckBox) findViewById(R.id.logon_password_preference);

        logonButton = (Button) findViewById(R.id.log_on_button);
        logonButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        logonProgressFakeButton = (Button) findViewById(R.id.logging_progress_button);

        if (BuildConfig.FLAVOR_VANILLA.equals(BuildConfig.FLAVOR)) {
            findViewById(R.id.link_text_sign_up).setVisibility(View.GONE);
            findViewById(R.id.link_text_forgot_password).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getFetLifeApplication().getEventBus().register(this);
        if (FetLifeApiIntentService.isActionInProgress(FetLifeApiIntentService.ACTION_APICALL_LOGON_USER)) {
            showProgress();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        getFetLifeApplication().getEventBus().unregister(this);
    }

    private void showProgress() {
        mUserNameView.setEnabled(false);
        mPasswordView.setEnabled(false);
        logonButton.setVisibility(View.GONE);
        logonProgressFakeButton.setVisibility(View.VISIBLE);
    }

    private void dismissProgress() {
        mUserNameView.setEnabled(true);
        mPasswordView.setEnabled(true);
        logonButton.setVisibility(View.VISIBLE);
        logonProgressFakeButton.setVisibility(View.GONE);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {

        getFetLifeApplication().setPasswordAlwaysPreference(passwordPreferenceCheckBox.isChecked());

        // Reset errors.
        mUserNameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String username = mUserNameView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            mUserNameView.setError(getString(R.string.error_field_required));
            focusView = mUserNameView;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress();
            FetLifeApiIntentService.startApiCall(this, FetLifeApiIntentService.ACTION_APICALL_LOGON_USER, username, password);
        }
    }

    public static void logout(FetLifeApplication fetLifeApplication) {
        fetLifeApplication.doHardLogout();
        verify(fetLifeApplication);
    }

    public static void verify(FetLifeApplication fetLifeApplication) {
        //TODO: add toast
        Intent intent = new Intent(fetLifeApplication, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        fetLifeApplication.startActivity(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoginStarted(LoginStartedEvent loginStartedEvent) {
        showProgress();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoginFinished(LoginFinishedEvent loginFinishedEvent) {
        //dismissProgress();
        ConversationsActivity.startActivity(this);
        finish();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLogonFailed(LoginFailedEvent loginFailedEvent) {
        //TODO: handle different errors
        dismissProgress();
        if (loginFailedEvent.isServerConnectionFailed()) {
            showToast(getResources().getString(R.string.error_connection_failed));
        } else {
            mPasswordView.setError(getString(R.string.error_incorrect_password));
        }
        mPasswordView.requestFocus();
    }

    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void onSignUp(View v) {
        openLink("https://fetlife.com/signup_step1");
    }

    public void onForgotLogin(View v) {
        openLink("https://fetlife.com/users/password/new");
    }

    private void openLink(String link) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(browserIntent);
    }

    private FetLifeApplication getFetLifeApplication() {
        return (FetLifeApplication) getApplication();
    }

}

