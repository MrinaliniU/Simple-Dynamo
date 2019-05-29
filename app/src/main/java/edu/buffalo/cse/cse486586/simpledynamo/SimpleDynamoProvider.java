package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

public class SimpleDynamoProvider extends ContentProvider {

	ConcurrentHashMap<String, String> dynamoData = new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> currentNodeStore = new ConcurrentHashMap<String, String>();
	static final int SERVER_PORT = 10000;
	Uri mUri;
	SharedPreferences settings;
	private String isFailed = "app_failed";
	private boolean wait = true;
	boolean isFailedProcess = false;
	private int queryReceivedFrom = 0;
	static String myPort;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if(selection.equals("@")) {
			currentNodeStore.clear();
		}
		else if(selection.equals("*")) {
			for(String port : HelperClass.redirectPorts) {
				sendMessage(setMessageVariable( Message.SELECTION.DELETE, selection, port, false));
			}
		} else {
			String position = HelperClass.getPort(selection);
			String[] delete = HelperClass.getPrefList(position);
			for(String port: delete) {
				sendMessage(setMessageVariable(Message.SELECTION.DELETE, selection, port,false));
			}
		}
		return 0;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		synchronized (this) {
			String key = values.getAsString("key");
			String value = values.getAsString("value");
			String position = HelperClass.getPort(key);
			String[] prefReplicate = HelperClass.getPrefList(position);
			for (String port : prefReplicate) {
				Message msg = setMessageVariable(Message.SELECTION.INSERT, key, port,false);
				msg.setValue(value);
				msg.setPort(myPort);
				sendMessage(msg);
			}
			return uri;
		}
	}

	@Override
	public boolean onCreate() {
		mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo");
		delete(mUri,"@",null);
		TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {
			return false;
		}
		/* Reference https://stackoverflow.com/questions/23934645/sharedpreferences-getboolean-returns-true-everytime */

		settings = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		if(appStarted()){
			setAppFailureMode("successful");
		}else{
			recoverStore();
		}
		return true;
	}

	private static Message setMessageVariable(int position, boolean isPredecessor, boolean isQuery, String... port){
		Message message = new Message();
		message.recoveryQuerySaved = new ConcurrentHashMap<String, String>();
		message.setPort(myPort);
		if(isQuery){
			message.setPreferenceListPort(port[0]);
			message.setSelection(Message.SELECTION.QUERY);
			message.setQuery_type(Message.QUERY_TYPE.QUERY_ALL);

		}else{
			message.setPreferenceListPort(HelperClass.getNode(myPort, position, isPredecessor));
			message.setSelection(Message.SELECTION.RECOVERY);
			message.setRecovery_type(Message.RECOVERY_TYPE.RECOVERY);

		}
		return message;
	}

	private static Message setMessageVariable(Message.SELECTION TYPE, String selection, String port, boolean isQuery){
		Message message = new Message();
		message.setKey(selection);
		message.setPreferenceListPort(port);
		message.setSelection(TYPE);
		if(isQuery){
			message.setQuery_type(Message.QUERY_TYPE.QUERY_ONE);
			message.setPort(myPort);
		}
		return message;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		synchronized (this) {
			MatrixCursor matrixCursor = new MatrixCursor(new String[]{"key", "value"});
			if (selection.equals("*")) {
				dynamoData = new ConcurrentHashMap<String, String>();
				for (String port : HelperClass.redirectPorts) {
					Message message = setMessageVariable(1,false,true,port);
					sendMessage(message);
				}
				while (wait) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				wait = true;
				for (Map.Entry<String, String> entry : dynamoData.entrySet()) {
					matrixCursor.addRow(new String[]{entry.getKey(), entry.getValue()});
				}
				dynamoData.clear();
				return matrixCursor;

			} else if (selection.equals("@")) {
				for (Map.Entry<String, String> entry : currentNodeStore.entrySet()) {
					matrixCursor.addRow(new String[]{entry.getKey(), entry.getValue()});
				}
			} else {
				String location = HelperClass.getPort(selection);
				String[] r = HelperClass.getPrefList(location);
				for (String port : r) {
					Message message = setMessageVariable(Message.SELECTION.QUERY,selection,port,true);
					sendMessage(message);
				}

				while (!dynamoData.containsKey(selection)) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				matrixCursor.addRow(new String[]{selection, dynamoData.get(selection)});
				dynamoData.clear();
			}
			return matrixCursor;
		}
	}
	private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			Socket socket = null;

			do{
				try{
					socket = serverSocket.accept();
					ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
					PrintWriter out = new PrintWriter(socket.getOutputStream());
					Message message = null;
					try {
						message = (Message) in.readObject();
						out.write("Got the msg at server " + message);
						out.flush();
						out.close();
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}

					Message.SELECTION TYPE = message.getSelection();

					switch (TYPE){
						case INSERT:
							currentNodeStore.put(message.getKey(), message.getValue());
							break;
						case QUERY:
							QueryTask(message);
							break;
						case DELETE:
							if(message.getKey().equals("*")) {
								currentNodeStore.clear();
							} else {
								currentNodeStore.remove(message.getKey());
							}
							break;
						case RECOVERY:
							recoveryTask(message);
							break;
					}

				}catch (IOException e){
					e.printStackTrace();
				}

			}while(!socket.isInputShutdown());

			return null;
		}

		private void recoveryTask(Message message){
			if(message.getRecovery_type().equals(Message.RECOVERY_TYPE.RECOVERY_DONE)){
				currentNodeStore.putAll(message.recoveryQuerySaved);
			}else{
				for (Map.Entry<String, String> entry : currentNodeStore.entrySet()) {
					if(HelperClass.isInPreferenceList(entry.getKey(), message.getPort())) {
						message.recoveryQuerySaved.put(entry.getKey(), entry.getValue());
					}
				}
				message.setPreferenceListPort(message.getPort());
				message.setRecovery_type(Message.RECOVERY_TYPE.RECOVERY_DONE);
				sendMessage(message);
			}
		}

		private void QueryTask(Message message){
			if(message.getQuery_type().equals(Message.QUERY_TYPE.QUERY_ONE)){
				message.setValue(currentNodeStore.get(message.getKey()));
				message.setPreferenceListPort(message.getPort());
				message.setQuery_type(Message.QUERY_TYPE.QUERY_ONE_DONE);
				sendMessage(message);
			}else if(message.getQuery_type().equals(Message.QUERY_TYPE.QUERY_ONE_DONE)){
				if(message.getKey()!=null && message.getValue() != null) {
					dynamoData.put(message.getKey(), message.getValue());
				}
			}else if(message.getQuery_type().equals(Message.QUERY_TYPE.QUERY_ALL)){
				message.recoveryQuerySaved.putAll(currentNodeStore);
				message.setQuery_type(Message.QUERY_TYPE.QUERY_DONE);
				message.setPreferenceListPort(message.getPort());
				sendMessage(message);
			}else if(message.getQuery_type().equals(Message.QUERY_TYPE.QUERY_DONE)){
				dynamoData.putAll(message.recoveryQuerySaved);
				queryReceivedFrom++;
				if(queryReceivedFrom == 4) {
					wait = false;
					queryReceivedFrom = 0;
				}
			}
		}
	}


	public void sendMessage(Message message)
	{
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);
	}

	public void recoverStore() {
		dynamoData = new ConcurrentHashMap<String, String>();
		Message messageA = setMessageVariable(2,true,false);
		sendMessage(messageA);
		Message messageB = setMessageVariable(1,true,false);
		sendMessage(messageB);
		Message messageC = setMessageVariable(1,false, false);
		sendMessage(messageC);
	}

	/*
       Used same code to build URI in OnPtestClickListener class
     */

	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

	public void setAppFailureMode(String status){
		if (status.equals("successful")){
			settings.edit().putBoolean(isFailed, false).apply();
		}else if (status.equals("failed")){
			settings.edit().putBoolean(isFailed, true).apply();
		}

	}

	public boolean appStarted(){
		return settings.getBoolean(isFailed,true);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

}
