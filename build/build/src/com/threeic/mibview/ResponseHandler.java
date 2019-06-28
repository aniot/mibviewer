package com.threeic.mibview;

import java.util.Map;

public interface ResponseHandler {
  public void onReceived(OidPair resp_values);
  public void onReceived(Map<String, String> map);
  public void onStats(int totalRequests, int totalResponses, long timeInMillis);
}
