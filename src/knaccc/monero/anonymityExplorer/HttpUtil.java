package knaccc.monero.anonymityExplorer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {

  public static Object jsonCall(String url, JSONObject callObj, boolean anticipatedArrayResult) {
    return jsonCall(url, callObj.toString(), anticipatedArrayResult);
  }
  public static Object jsonCall(String url, String callObjString, boolean anticipatedArrayResult) {

    HttpURLConnection conn = null;
    try {
      URL urlObj = new URL(url);

      conn = (HttpURLConnection) urlObj.openConnection();

      conn.setConnectTimeout(2000);
      conn.setReadTimeout(2000);

      conn.setAllowUserInteraction(false);

      conn.setRequestProperty("Content-type", "application/json; charset=utf-8");
      conn.setRequestMethod("POST");

      conn.setUseCaches(false);
      conn.setDoInput(true);
      conn.setDoOutput(true);

      conn.connect();

      DataOutputStream wr = new DataOutputStream (conn.getOutputStream ());
      wr.writeBytes(callObjString);
      wr.flush();
      wr.close();

      int statusCode = conn.getResponseCode();

      BufferedReader br = new BufferedReader(new InputStreamReader(statusCode==200?conn.getInputStream():conn.getErrorStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
          sb.append(line+"\n");
      }
      br.close();
      String responseString = sb.toString();

      if(anticipatedArrayResult) {
        try {
          return new JSONArray(responseString);
        }
        catch (Exception e) {
          return new JSONObject(responseString);
        }
      }
      else {
        try {
          return new JSONObject(responseString);
        }
        catch (Exception e) {
          return new JSONArray(responseString);
        }
      }

    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      conn.disconnect();
    }

  }



}
