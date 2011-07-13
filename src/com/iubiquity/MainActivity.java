package com.iubiquity;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.MethodOverride;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.iubiquity.spreadsheet.client.AndroidSpreadsheetClient;
import com.iubiquity.spreadsheets.client.SpreadsheetClient;

public class MainActivity extends Activity {

	public static final String TAG = MainActivity.class.getSimpleName();

	/** Logging level for HTTP requests/responses. */
	private static final Level LOGGING_LEVEL = Level.ALL;

	private static final String AUTH_TOKEN_TYPE = "wise";
	private static final int REQUEST_AUTHENTICATE = 0;

	private static GoogleAccountManager accountManager;

	public static SpreadsheetClient client;

	private String authToken;
	private String accountName;

	private final Context context = this;

	private int k;

	private SharedPreferences settings;

	public static final String PREF_ACCOUNT_NAME = "accountName";
	public static final String PREF_AUTH_TOKEN = "authToken";

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Logger.getLogger("com.google.api.client").setLevel(
				MainActivity.LOGGING_LEVEL);
		MainActivity.accountManager = new GoogleAccountManager(this);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.authToken = settings.getString(MainActivity.PREF_AUTH_TOKEN, null);
		this.accountName = settings.getString(MainActivity.PREF_ACCOUNT_NAME,
				null);
		this.createClient(this, MainActivity.accountManager);
		this.setContentView(R.layout.main);
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.getAccount();
	}

	private void chooseAccount() {
		MainActivity.accountManager.manager.getAuthTokenByFeatures(
				GoogleAccountManager.ACCOUNT_TYPE,
				MainActivity.AUTH_TOKEN_TYPE, null, MainActivity.this, null,
				null, new AccountManagerCallback<Bundle>() {

					public void run(final AccountManagerFuture<Bundle> future) {
						Bundle bundle;
						try {
							bundle = future.getResult();
							MainActivity.this.setAccountName(bundle
									.getString(AccountManager.KEY_ACCOUNT_NAME));
							MainActivity.this.setAuthToken(bundle
									.getString(AccountManager.KEY_AUTHTOKEN));
						} catch (final OperationCanceledException e) {
							// user canceled
							Log.d(TAG, "Line 87 " + e.getMessage());
						} catch (final AuthenticatorException e) {
							Log.d(TAG, "Line 89 " + e.getMessage());
							MainActivity.this.handleException(e);
						} catch (final IOException e) {
							Log.d(TAG, "Line 92 " + e.getMessage());
							MainActivity.this.handleException(e);
						}
					}
				}, null);
	}

	private void createClient(final Context context,
			final GoogleAccountManager gaccountManager) {

		final HttpTransport transport = AndroidHttp.newCompatibleTransport();

		MainActivity.client = new AndroidSpreadsheetClient(
				transport.createRequestFactory(new HttpRequestInitializer() {

					public void initialize(final HttpRequest request) {
						final GoogleHeaders headers = new GoogleHeaders();
						headers.setApplicationName(MainActivity.this
								.getString(R.string.app_name));
						headers.gdataVersion = "2";
						request.headers = headers;
						MainActivity.client.initializeParser(request);
						request.interceptor = new HttpExecuteInterceptor() {

							public void intercept(final HttpRequest request)
									throws IOException {
								final GoogleHeaders headers = (GoogleHeaders) request.headers;
								Log.d(TAG, "setting authToken in Header: "
										+ authToken);
								headers.setGoogleLogin(authToken);
								new MethodOverride().intercept(request);
							}
						};

						request.unsuccessfulResponseHandler = new HttpUnsuccessfulResponseHandler() {
							public boolean handleResponse(
									final HttpRequest request,
									final HttpResponse response,
									final boolean retrySupported) {

								if (response.statusCode == 401) {
									Log.d(TAG, "invalidating token "
											+ authToken);
									gaccountManager
											.invalidateAuthToken(authToken);
									settings.edit()
											.remove(MainActivity.PREF_AUTH_TOKEN)
											.commit();
									final Intent intent = new Intent(context,
											MainActivity.class);
									intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
									MainActivity.this.startActivity(intent);
								}
								return false;
							}
						};
					}
				}));
	}

	private void getAccount() {
		final Account account = MainActivity.accountManager
				.getAccountByName(this.accountName);
		Log.d(TAG, "getAccount " + this.accountName + " try no :" + k);
		if (account != null) {
			// handle invalid token
			if (this.authToken == null) {
				MainActivity.accountManager.manager.getAuthToken(account,
						MainActivity.AUTH_TOKEN_TYPE, true,
						new AccountManagerCallback<Bundle>() {

							public void run(
									final AccountManagerFuture<Bundle> future) {
								try {
									final Bundle bundle = future.getResult();
									if (bundle
											.containsKey(AccountManager.KEY_INTENT)) {
										final Intent intent = bundle
												.getParcelable(AccountManager.KEY_INTENT);
										int flags = intent.getFlags();
										flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
										intent.setFlags(flags);
										MainActivity.this
												.startActivityForResult(
														intent,
														MainActivity.REQUEST_AUTHENTICATE);
									} else if (bundle
											.containsKey(AccountManager.KEY_AUTHTOKEN)) {
										MainActivity.this.setAuthToken(bundle
												.getString(AccountManager.KEY_AUTHTOKEN));

									}
									k = 0;
								} catch (final Exception e) {
									Toast.makeText(context, e.toString(),
											Toast.LENGTH_LONG);
									MainActivity.this.handleException(e);
								}
							}
						}, null);
			}
			return;
		}
		this.chooseAccount();
	}

	private void handleException(final Exception e) {

		if (e instanceof HttpResponseException) {
			final HttpResponse response = ((HttpResponseException) e).response;
			final int statusCode = response.statusCode;
			try {
				response.ignore();
			} catch (final IOException e1) {
				Toast.makeText(context, e1.toString(), Toast.LENGTH_SHORT);
			}
			// TODO: should only try this once to avoid infinite loop
			if (statusCode == 401) {
				k++;
				if (k <= 1) {
					this.getAccount();
				}
				return;
			}
		} else {
			Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT);
		}
		Log.e(MainActivity.TAG, e.getMessage(), e);
	}

	@Override
	protected void onActivityResult(final int requestCode,
			final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(MainActivity.TAG, "onActivityResult");
		if (requestCode == MainActivity.REQUEST_AUTHENTICATE) {
			if (resultCode == Activity.RESULT_OK) {
				this.getAccount();
			} else {
				this.chooseAccount();
			}
		}
	}

	private void setAccountName(final String accountName) {
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(MainActivity.PREF_ACCOUNT_NAME, accountName);
		editor.commit();
		this.accountName = accountName;
	}

	private void setAuthToken(final String authToken) {
		Log.d(TAG, "saving authToken " + authToken);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(MainActivity.PREF_AUTH_TOKEN, authToken);
		editor.commit();
		this.authToken = authToken;
	}

}