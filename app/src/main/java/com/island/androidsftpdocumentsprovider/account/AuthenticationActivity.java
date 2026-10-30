package com.island.androidsftpdocumentsprovider.account;

/* This file is part of SFTP-SAF, an Android app to access sftp servers via Storage access framework
 Copyright (C) 2025,2026 Alain Knaff
 Copyright (C) 2020      Riccardo Isola

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

import java.util.List;
import java.io.IOException;
import java.net.ConnectException;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.island.sftp.SFTP;
import com.island.sftp.InteractiveUserInfo;
import com.island.androidsftpdocumentsprovider.provider.ProviderActivity;

import com.island.util.ErrorDialog;
import lu.knaff.alain.saf_sftp.R;

public class AuthenticationActivity extends ProviderActivity
{
	private Dao dao;
	private Account account = null;

	// id field name for intents
	public static final String ID_COL = "id";

	private static final int PROXY_TYPE_NONE = 0;
	private static final int PROXY_TYPE_SOCKS = 1;
	private static final int PROXY_TYPE_JUMP_HOST = 2;

	// keys for the instance state (the dropdowns do not save their own)
	private static final String STATE_PROXY_TYPE = "proxy_type";
	private static final String STATE_JUMP_HOST = "jump_host_account_id";

	private int proxyType = PROXY_TYPE_NONE;
	private Integer jumpHostAccountId = null;

	private String[] proxyTypeNames;
	private List<Account> jumpHosts;
	private MaterialAutoCompleteTextView proxyTypeView;
	private MaterialAutoCompleteTextView jumpHostView;

	@Override
	protected void onCreate(@Nullable Bundle icicle)
	{
		super.onCreate(icicle);
		dao = TheDatabase.getDao(getApplicationContext());
		setContentView(R.layout.authentication_activity);
		applySystemBarInsets(findViewById(R.id.root));
		int accountId=getIntent()
			.getIntExtra(AuthenticationActivity.ID_COL,-1);

		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(accountId == -1 ?
				 R.string.add_account :
				 R.string.update_account);
		toolbar.setNavigationOnClickListener(
			v -> getOnBackPressedDispatcher().onBackPressed());

		findViewById(R.id.add_account)
		    .setVisibility(accountId == -1 ? View.VISIBLE : View.GONE);
		findViewById(R.id.add_account)
		    .setEnabled(false);
		findViewById(R.id.update_account)
		    .setVisibility(accountId != -1 ? View.VISIBLE : View.GONE);

		// Proxy type: "exposed dropdown menu"
		proxyTypeView = findViewById(R.id.proxy_type);
		proxyTypeNames = getResources()
			.getStringArray(R.array.proxy_type_array);
		proxyTypeView.setSimpleItems(proxyTypeNames);
		proxyTypeView.setOnItemClickListener(
			(parent, view, position, id) -> setProxyType(position));

		// Jump host: same kind of dropdown, listing the accounts that
		// may be used as jump host
		jumpHosts = dao.getAllEligibleJumpHosts(accountId);
		String[] jumpHostNames = new String[jumpHosts.size()];
		for(int i = 0; i < jumpHostNames.length; i++)
			jumpHostNames[i] = jumpHosts.get(i).toString();
		jumpHostView = findViewById(R.id.jump_host);
		jumpHostView.setSimpleItems(jumpHostNames);
		jumpHostView.setOnItemClickListener(
			(parent, view, position, id) -> selectJumpHost(position));

		int initialProxyType = PROXY_TYPE_NONE;
		Integer initialJumpHostId = null;

		if(accountId != -1)
		{
			account=dao.readAccountById(accountId);
			EditText displayName=findViewById(R.id.displayName);

			EditText host=findViewById(R.id.host);
			EditText port=findViewById(R.id.port);
			EditText user=findViewById(R.id.user);
			EditText directory=findViewById(R.id.start_directory);
			EditText socksProxy=findViewById(R.id.socks_proxy);
			CompoundButton hideFromList=findViewById(R.id.hide_from_list);

			displayName.setText(account.getDisplayName());
			host.setText(account.getHostName());
			user.setText(account.getUserName());
			port.setText(String.valueOf(account.getPort()));
			directory.setText(account.getDirectory());
			String socksProxyString = account.getSocksProxy();
			initialJumpHostId = account.getJumpHostId();
			if(! "".equals(socksProxyString)) {
				initialProxyType = PROXY_TYPE_SOCKS;
				socksProxy.setText(socksProxyString);
			} else if(initialJumpHostId != null) {
				initialProxyType = PROXY_TYPE_JUMP_HOST;
			}

			hideFromList.setChecked(account.getHideFromList());
		}

		// After a configuration change (rotation, ...) keep the choices
		// the user made
		if(icicle != null) {
			initialProxyType = icicle.getInt(STATE_PROXY_TYPE,
							 initialProxyType);
			if(icicle.containsKey(STATE_JUMP_HOST))
				initialJumpHostId = icicle.getInt(STATE_JUMP_HOST);
		}
		setProxyType(initialProxyType);
		selectJumpHostById(initialJumpHostId);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_PROXY_TYPE, proxyType);
		if(jumpHostAccountId != null)
			outState.putInt(STATE_JUMP_HOST, jumpHostAccountId);
	}

	public void checkHostKey(@NonNull View view) {
		findViewById(R.id.check_host_key)
		    .setEnabled(false);

		String displayName=((EditText)findViewById(R.id.displayName))
			.getText().toString();

		String hostName=((EditText)findViewById(R.id.host))
			.getText().toString();

		String portString=((EditText)findViewById(R.id.port))
			.getText().toString();
		int port = Integer.parseInt(portString);

		String userName=((EditText)findViewById(R.id.user))
			.getText().toString();

		String password=((EditText)findViewById(R.id.password))
			.getText().toString();
		String directory=((EditText)findViewById(R.id.start_directory))
			.getText().toString();
		String socksProxy=((EditText)findViewById(R.id.socks_proxy))
			.getText().toString();

		Account account = new Account("test",
					      displayName,
					      hostName, port,
					      userName, password,
					      directory,
					      (proxyType==PROXY_TYPE_SOCKS) ? socksProxy : "",
					      (proxyType==PROXY_TYPE_JUMP_HOST) ? jumpHostAccountId : null,
					      false);

		new Thread(() -> {
			SFTP sftp=null;
			try {
				sftp = new SFTP(this, null, account,
						new InteractiveUserInfo(this));
			} catch(ConnectException e) {
				ErrorDialog.showError(this,
						      getString(R.string.test_connect_err),
						      e);
			}
			boolean success = sftp != null;
			if(sftp != null)
				try {
					sftp.close();
				} catch(IOException e) {
					// Problems during close can
					// safely be ignored
				}
			runOnUiThread(()->{
					findViewById(R.id.check_host_key)
						.setEnabled(true);
					if(success)
						findViewById(R.id.add_account)
							.setEnabled(true);
				});
		}).start();
	}

	/**
	 * Selects a proxy type, and shows the input that goes with it
	 */
	private void setProxyType(int position) {
		if(position < 0 || position >= proxyTypeNames.length)
			position = PROXY_TYPE_NONE;
		proxyType = position;
		// filter=false: show the text without narrowing the list of
		// choices down to it
		proxyTypeView.setText(proxyTypeNames[position], false);

		findViewById(R.id.socks_proxy_layout)
			.setVisibility(position == PROXY_TYPE_SOCKS ?
				       View.VISIBLE : View.GONE);
		findViewById(R.id.jump_host_layout)
			.setVisibility(position == PROXY_TYPE_JUMP_HOST ?
				       View.VISIBLE : View.GONE);
	}

	/**
	 * Selects the jump host with the given account id. Falls back to the
	 * first one in the list if there is no such account (or no id)
	 */
	private void selectJumpHostById(@Nullable Integer id) {
		int position = 0;
		if(id != null)
			for(int i = 0; i < jumpHosts.size(); i++)
				if(jumpHosts.get(i).getId() == id) {
					position = i;
					break;
				}
		selectJumpHost(position);
	}

	private void selectJumpHost(int position) {
		if(position < 0 || position >= jumpHosts.size()) {
			jumpHostAccountId = null;
			jumpHostView.setText("", false);
			return;
		}
		Account acct = jumpHosts.get(position);
		jumpHostAccountId = acct.getId();
		jumpHostView.setText(acct.toString(), false);
	}

	public void confirm(@NonNull View view) {
		try {
			_confirm();
		} catch(Exception e) {
			ErrorDialog.showError(this, "Error saving account", e);
		}
	}

	private void _confirm() {
		String displayName=((EditText)findViewById(R.id.displayName))
			.getText().toString();

		String hostName=((EditText)findViewById(R.id.host))
			.getText().toString();

		String portString=((EditText)findViewById(R.id.port))
			.getText().toString();

		String userName=((EditText)findViewById(R.id.user))
			.getText().toString();

		String password=((EditText)findViewById(R.id.password))
			.getText().toString();
		String directory=((EditText)findViewById(R.id.start_directory))
			.getText().toString();
		String socksProxy=((EditText)findViewById(R.id.socks_proxy))
			.getText().toString();
		boolean hideFromList = ((CompoundButton)findViewById(R.id.hide_from_list))
			.isChecked();
		if(hostName.isEmpty()||portString.isEmpty()||userName.isEmpty())
			return;
		int port = Integer.parseInt(portString);
		if(account != null) {
			// this is a request to edit an existing account

			// if nothing changed, exit
			if(displayName.equals(account.getDisplayName()) &&
			   hostName.equals(account.getHostName()) &&
			   userName.equals(account.getUserName()) &&
			   port == account.getPort() &&
			   password.isEmpty() &&
			   directory.equals(account.getDirectory()) &&
			   (proxyType==PROXY_TYPE_SOCKS ? socksProxy : "").equals( account.getSocksProxy()) &&
			   (proxyType==PROXY_TYPE_JUMP_HOST ? jumpHostAccountId : null) ==  account.getJumpHostId() &&
			   hideFromList == account.getHideFromList()
			   ) {
				Toast.makeText(this,
					       R.string.nothing_changed,
					       Toast.LENGTH_SHORT)
					.show();
				return;
			}
		}

		String name = userName+"@"+hostName+":"+port;
		if(account == null) {
			dao.insertAll(new Account(name, displayName,
						  hostName, port,
						  userName, password,
						  directory,
						  (proxyType==PROXY_TYPE_SOCKS)?socksProxy:"",
						  (proxyType==PROXY_TYPE_JUMP_HOST)?jumpHostAccountId:null,
						  hideFromList
						  ));
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_INSERT;
			notifyChange(flags);
		} else {
			// update existing account
			account.setName(name);
			account.setDisplayName(displayName);
			account.setHostName(hostName);
			account.setPort(port);
			account.setUserName(userName);
			if(!password.isEmpty())
				account.setPassword(password);
			account.setDirectory(directory);
			account.setSocksProxy((proxyType==PROXY_TYPE_SOCKS)?socksProxy:"");
			if(proxyType==PROXY_TYPE_JUMP_HOST) {
				if(dao.isDescendant(jumpHostAccountId, account.getId()))
					throw new IllegalArgumentException("Would cause loop in jump hosts");
				if(jumpHostAccountId==account.getId())
					throw new IllegalArgumentException("Account cannot be jumphost for itself");
			}

			account.setJumpHostId((proxyType==PROXY_TYPE_JUMP_HOST)?jumpHostAccountId:null);
			account.setHideFromList(hideFromList);
			dao.update(account);
			int flags=0;
			if(Build.VERSION.SDK_INT>=30)
			    flags |= ContentResolver.NOTIFY_UPDATE;
			notifyChange(flags);
		}

		Intent result=new Intent();
		setResult(RESULT_OK,result);
		finish();
	}

}
