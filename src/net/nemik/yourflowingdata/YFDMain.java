/*
 * Nemanja Stefanovic 2010
 *  
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details. 
 * 
 */

package net.nemik.yourflowingdata;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;
import android.widget.AdapterView.AdapterContextMenuInfo;


public class YFDMain extends ListActivity
{	
	public static final String PREFS_NAME = "YFDPrefs";

	private YFDDBAdapter mdbhelper;
	
	public static final String TOKEN = "token";
	public static final String SECRET = "secret";
	public static final String CALLBACK_URL = "YFD://twitter";
	public static final String OAUTH_VERIFIER = "oauth_verifier";
	
	private static final String TAG = "OAUTH";

	public static final String USER_TOKEN = "user_token";
	public static final String USER_SECRET = "user_secret";
	public static final String REQUEST_TOKEN = "request_token";
	public static final String REQUEST_SECRET = "request_secret";

	
	public static final int INSERT_ID = Menu.FIRST;
	public static final int SEND_ID = Menu.FIRST+1;
	public static final int DELETE_ID = Menu.FIRST+2;
	
	String token;
	String secret;
	
	Context mContext = this;
	
	CommonsHttpOAuthConsumer consumer;
    OAuthProvider provider;
    
    SharedPreferences settings;
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		
		consumer = new CommonsHttpOAuthConsumer("zzzNjnbayp7erOjgPv0Q", "eLOBBH8gP9ZkvuCz9Wv2ltk8wY2Ps2858C2Odj5IU1Q");
		provider = new DefaultOAuthProvider("http://twitter.com/oauth/request_token", 
	    		"http://twitter.com/oauth/access_token",
	            "http://twitter.com/oauth/authorize");
		provider.setOAuth10a(true);
		
		String token = settings.getString(TOKEN, "");
	    String secret = settings.getString(SECRET, "");
	    
		if (token == "" || secret == "")
		{
			Intent i = this.getIntent();
			if (i.getData() == null)
			{
				sendToTwitterOAuth();
			}
		} 
		else
		{
			this.token = token;
			this.secret = secret;
			consumer.setTokenWithSecret(this.token, this.secret);
		}
		
		setContentView(R.layout.yfd_main_list);
		mdbhelper = new YFDDBAdapter(this);
		mdbhelper.open();
		fillData();
		registerForContextMenu(getListView());
	}
	
	@Override
	protected void onResume() 
	{	
		super.onResume();
		
		// this must be places in activity#onResume()
		Uri uri = this.getIntent().getData();
		if (uri != null && uri.toString().startsWith(CALLBACK_URL))
		{
			String token = settings.getString(REQUEST_TOKEN, null);
			String secret = settings.getString(REQUEST_SECRET, null);
			
		    try
			{  
		    	if(!(token == null || secret == null)) 
		    	{
		    		consumer.setTokenWithSecret(token, secret);
				}
		    	
		    	String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
				String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
				
				//Assert.assertEquals(otoken, consumer.getToken());
				
		    	provider.retrieveAccessToken(consumer, verifier);
				this.token = consumer.getToken();
				this.secret = consumer.getTokenSecret();
				
				//add to preferences
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString(TOKEN, this.token);
				editor.putString(SECRET, this.secret);
				editor.commit();
				
				//clear old
				saveRequestInformation(settings, null, null);
				
				Toast.makeText(getApplicationContext(), "Twitter authentication successful!", Toast.LENGTH_SHORT).show();
			}
		    catch (Exception e)
			{
		    	Log.e("Error on resume", e.getMessage());
			}
		    finally 
		    {
		    	Intent i = new Intent(this, YFDMain.class);
		    	startActivity(i);
		    	finish();
		    }
		}
	}
	
	public static void saveRequestInformation(SharedPreferences settings, String token, String secret)
	{
		// null means to clear the old values
		SharedPreferences.Editor editor = settings.edit();
		if (token == null)
		{
			editor.remove(REQUEST_TOKEN);
			Log.d(TAG, "Clearing Request Token");
		} 
		else
		{
			editor.putString(REQUEST_TOKEN, token);
			Log.d(TAG, "Saving Request Token: " + token);
		}
		if (secret == null)
		{
			editor.remove(REQUEST_SECRET);
			Log.d(TAG, "Clearing Request Secret");
		} 
		else
		{
			editor.putString(REQUEST_SECRET, secret);
			Log.d(TAG, "Saving Request Secret: " + secret);
		}
		editor.commit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, INSERT_ID, 0, R.string.menu_insert);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case INSERT_ID:
			{
				AlertDialog.Builder alert = new AlertDialog.Builder(this);

				alert.setTitle("New Action");
				// Set an EditText view to get user input
				final EditText input = new EditText(this);
				alert.setView(input);
				alert.setMessage("put a '%d' in your action to be prompted to enter data later.\nExample: 'ate %d turtles'");

				alert.setPositiveButton("Add",
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog,
									int whichButton)
							{
								String value = input.getText().toString();
								createNote(value);
								Toast.makeText(getApplicationContext(), "Action added", Toast.LENGTH_SHORT).show();
							}
						});

				alert.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog,
									int whichButton)
							{
								// Canceled.
							}
						});

				alert.show();
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void createNote(String name)
	{
		String noteName = name;
		mdbhelper.createNote(noteName, "");
		fillData();
	}
	
	public void fillData()
	{
		Cursor c = mdbhelper.fetchAllNotes();
		startManagingCursor(c);
		String [] from = new String [] {YFDDBAdapter.KEY_TITLE};
		int [] to = new int [] {R.id.text1};
		
		SimpleCursorAdapter actions = new SimpleCursorAdapter(this, R.layout.action_row, c, from, to);
		setListAdapter(actions);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, SEND_ID, 0, "Send");
		menu.add(0, DELETE_ID, 0, "Delete");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case DELETE_ID:
			{
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				
				AlertDialog.Builder alert = new AlertDialog.Builder(this);

				alert.setTitle("Confirm");
				alert.setMessage("Are you sure you want to delete this action?");
				alert.setPositiveButton("Yes", new AreYouSureDelete(this, mdbhelper, info.id));

				alert.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog,
									int whichButton)
							{
								// Canceled.
							}
						});

				alert.show();
				return true;
			}
			case SEND_ID:
			{
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
				
				Cursor c = mdbhelper.fetchNote(info.id);
				String title = c.getString(1);
				
				final String [] splits = (title+" ").split("%d");
				
				if(splits.length == 1)
				{
					String status = "d yfd "+title;
					if(postTwitter(status))
					{
						Toast.makeText(getApplicationContext(), "Action sent: "+ title, Toast.LENGTH_SHORT).show();
					}
					else
					{
						Toast.makeText(getApplicationContext(), "ERROR sending action", Toast.LENGTH_SHORT).show();
					}				
				}
				
				else
				{
					AlertDialog.Builder alert = new AlertDialog.Builder(this);

					alert.setTitle("Send Action");
					
					final ArrayList<View> edits = new ArrayList<View>();
					
					LinearLayout ll = new LinearLayout(getApplicationContext());
					int i = 0;
					for(String s: splits)
					{
						TextView tv = new TextView(this);
						tv.setText(s);
						ll.addView(tv);
						
						if(i != splits.length-1)
						{
							EditText input = new EditText(this);
							edits.add((View)input);
							ll.addView(input);
						}
						i++;
					}
					
					alert.setView(ll);

					alert.setPositiveButton("Send",
							new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog, int whichButton)
								{
									String status = "d yfd ";
									
									int i = 0;
									for(View input : edits)
									{
										EditText iinput = (EditText)input;
										status += splits[i] + iinput.getText().toString();
										i++;
									}
									status += splits[splits.length-1];
									
									postTwitter(status);

									Toast.makeText(getApplicationContext(),
											"Action sent: \""+status+"\"", Toast.LENGTH_SHORT)
											.show();
								}
							});

					alert.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog,
										int whichButton)
								{
									// Canceled.
								}
							});
					c.close();
					alert.show();
					return true;
				}
				c.close();
			}
		}
		return super.onContextItemSelected(item);
	}

	public boolean postTwitter(String status)
	{
		HttpClient client = new DefaultHttpClient();
		try
		{
			// create a request that requires authentication
			HttpPost post = new HttpPost("http://twitter.com/statuses/update.xml");
			final List<BasicNameValuePair> nvps = new ArrayList<BasicNameValuePair>();
			// 'status' here is the update value you collect from UI
			nvps.add(new BasicNameValuePair("status", status));
			post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			// set this to avoid 417 error (Expectation Failed)
			post.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
			// sign the request
			consumer.sign(post);
			// send the request
			final HttpResponse response = client.execute(post);
			// response status should be 200 OK
			int statusCode = response.getStatusLine().getStatusCode();
			final String reason = response.getStatusLine().getReasonPhrase();
			// release connection
			response.getEntity().consumeContent();
			if (statusCode != 200)
			{
				Log.e("TwitterConnector", reason);
				if(reason.trim().equals("Unauthorized"))
				{
					//clear preferences of these bad credentials
					SharedPreferences.Editor editor = settings.edit();
					editor.putString(TOKEN, "");
					editor.putString(SECRET, "");
					editor.commit();
					
					AlertDialog.Builder alert = new AlertDialog.Builder(this);
					alert.setTitle("Re-Authorize");
					alert.setMessage("Send failed due to bad authentication. Please re-authorize this app with Twitter.");

					alert.setPositiveButton("OK",
							new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog,
										int whichButton)
								{
									sendToTwitterOAuth();
								}
							});

					alert.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog,
										int whichButton)
								{
									// Canceled.
								}
							});

					alert.show();
					
				}
				return false;
			}
			return true;
		} 
		catch (OAuthException e)
		{
			//clear preferences of these bad credentials
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putString(TOKEN, "");
			editor.putString(SECRET, "");
			editor.commit();
			Log.e("sending error ",e.getMessage());
			return false;
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	public void sendToTwitterOAuth()
	{
		try
		{
			String authUrl = provider.retrieveRequestToken(consumer, CALLBACK_URL);
			saveRequestInformation(settings, consumer.getToken(), consumer.getTokenSecret());
			mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
		} 
		catch (Exception e)
		{
			Log.e("ERRORRR", e.getMessage());
			Toast.makeText(getApplicationContext(), 
					"Error reaching twitter.com. This app needs access to the internet to work",
					Toast.LENGTH_SHORT).show();
		}
	}

	public static class AreYouSureDelete implements DialogInterface.OnClickListener
	{
		private long id;
		private YFDDBAdapter helper;
		private YFDMain yfdmain;
		
		public AreYouSureDelete(YFDMain main, YFDDBAdapter helper, long id)
		{
			this.yfdmain = main;
			this.helper = helper;
			this.id = id;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			helper.deleteNote(id);
			yfdmain.fillData();
			Toast.makeText(yfdmain, "Action deleted", Toast.LENGTH_SHORT).show();
		}
	}
}
