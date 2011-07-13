package com.iubiquity.spreadsheet.client;

import com.google.api.client.http.HttpRequestFactory;
import com.iubiquity.spreadsheets.client.SpreadsheetClient;

public class AndroidSpreadsheetClient extends SpreadsheetClient {

	public AndroidSpreadsheetClient(HttpRequestFactory requestFactory) {
		super.requestFactory = requestFactory;
	}

}
