/* Copyright (c) 2008-2011 -- CommonsWare, LLC

	 Licensed under the Apache License, Version 2.0 (the "License");
	 you may not use this file except in compliance with the License.
	 You may obtain a copy of the License at

		 http://www.apache.org/licenses/LICENSE-2.0

	 Unless required by applicable law or agreed to in writing, software
	 distributed under the License is distributed on an "AS IS" BASIS,
	 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 See the License for the specific language governing permissions and
	 limitations under the License.
*/
	 
package com.commonsware.android.constants;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Provider extends ContentProvider {
	private static final int CONSTANTS=1;
	private static final int CONSTANT_ID=2;
	private static final UriMatcher MATCHER;
	
	public static final class Constants implements BaseColumns {
		public static final Uri CONTENT_URI
				 =Uri.parse("content://com.commonsware.android.constants.Provider/constants");
		public static final String DEFAULT_SORT_ORDER="title";
		public static final String TITLE="title";
		public static final String VALUE="value";
	}

	static {
		MATCHER=new UriMatcher(UriMatcher.NO_MATCH);
		MATCHER.addURI("com.commonsware.android.constants.Provider", "constants", CONSTANTS);
		MATCHER.addURI("com.commonsware.android.constants.Provider", "constants/#", CONSTANT_ID);
	}
	
	private SQLiteDatabase db=null;
	
	@Override
	public boolean onCreate() {
		db=(new DatabaseHelper(getContext())).getWritableDatabase();
		
		return((db == null) ? false : true);
	}
	
	@Override
	public Cursor query(Uri url, String[] projection, String selection,
												String[] selectionArgs, String sort) {
		SQLiteQueryBuilder qb=new SQLiteQueryBuilder();

		qb.setTables(getTableName());
		
		if (!isCollectionUri(url)) {
			qb.appendWhere(getIdColumnName()+"="+url.getPathSegments().get(1));
		}
		
		String orderBy;
		
		if (TextUtils.isEmpty(sort)) {
			orderBy=getDefaultSortOrder();
		}
		else {
			orderBy=sort;
		}

		Cursor c=qb.query(db, projection, selection, selectionArgs,
											null, null, orderBy);
		c.setNotificationUri(getContext().getContentResolver(), url);
		return c;
	}

	@Override
	public String getType(Uri url) {
		if (isCollectionUri(url)) {
			return(getCollectionType());
		}
		
		return(getSingleType());
	}

	@Override
	public Uri insert(Uri url, ContentValues initialValues) {
		long rowID;
		ContentValues values;
		
		if (initialValues!=null) {
			values=new ContentValues(initialValues);
		}
		else {
			values=new ContentValues();
		}

		if (!isCollectionUri(url)) {
			throw new IllegalArgumentException("Unknown URL " + url);
		}
		
		for (String colName : getRequiredColumns()) {
			if (values.containsKey(colName) == false) {
				throw new IllegalArgumentException("Missing column: "+colName);
			}
		}

		populateDefaultValues(values);

		rowID=db.insert(getTableName(), Constants.TITLE, values);
		
		if (rowID>0) {
			Uri uri=ContentUris.withAppendedId(getContentUri(), rowID);
			getContext().getContentResolver().notifyChange(uri, null);
			
			return(uri);
		}

		throw new SQLException("Failed to insert row into " + url);
	}

	@Override
	public int delete(Uri url, String where, String[] whereArgs) {
		int count;
		long rowId=0;
		
		if (isCollectionUri(url)) {
			count=db.delete(getTableName(), where, whereArgs);
		}
		else {
			String segment=url.getPathSegments().get(1);
			
			rowId=Long.parseLong(segment);
			count=db
					.delete(getTableName(), getIdColumnName()+"="
							+ segment
							+ (!TextUtils.isEmpty(where) ? " AND (" + where
									+ ')' : ""), whereArgs);
		}

		getContext().getContentResolver().notifyChange(url, null);
		
		return(count);
	}

	@Override
	public int update(Uri url, ContentValues values,
										String where, String[] whereArgs) {
		int count;
		
		if (isCollectionUri(url)) {
			count=db.update(getTableName(), values, where, whereArgs);
		}
		else {
			String segment=url.getPathSegments().get(1);
			count=db
					.update(getTableName(), values, getIdColumnName()+"="
							+ segment
							+ (!TextUtils.isEmpty(where) ? " AND (" + where
									+ ')' : ""), whereArgs);
		}
	
		getContext().getContentResolver().notifyChange(url, null);
		
		return(count);
	}
	
	private boolean isCollectionUri(Uri url) {
		return(MATCHER.match(url)==CONSTANTS);
	}
	
	private String getTableName() {
		return("constants");
	}
	
	private String getIdColumnName() {
		return(Constants._ID);
	}
	
	private String getDefaultSortOrder() {
		return(Constants.TITLE);
	}
	
	private String getCollectionType() {
		return("vnd.commonsware.cursor.dir/constant");
	}
	
	private String getSingleType() {
		return("vnd.commonsware.cursor.item/constant");
	}
	
	private String[] getRequiredColumns() {
		return(new String[] {Constants.TITLE});
	}
	
	private void populateDefaultValues(ContentValues values) {
		if (!values.containsKey(Provider.Constants.VALUE)) {
			values.put(Provider.Constants.VALUE, 0.0f);
		}
	}
	
	private Uri getContentUri() {
		return(Provider.Constants.CONTENT_URI);
	}
}