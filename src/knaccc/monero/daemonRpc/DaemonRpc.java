package knaccc.monero.daemonRpc;

import knaccc.monero.anonymityExplorer.Block;
import knaccc.monero.anonymityExplorer.Transaction;
import knaccc.monero.anonymityExplorer.TransactionCursor;
import knaccc.monero.util.ByteUtil;
import knaccc.monero.anonymityExplorer.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DaemonRpc {
  
  public static final int hour = 30; // 30*24 blocks per day on average
  public static final int day = hour * 24; // 30*24 blocks per day on average
  public static final int week = day * 7;

  private final String endpointUrlPrefix;

  public DaemonRpc(String endpointUrlPrefix) {
    this.endpointUrlPrefix = endpointUrlPrefix;
  }

  public JSONObject daemonJsonRpcCall(String method, JSONObject callParams) {
    JSONObject callObj = new JSONObject();
    callObj.put("jsonrpc", "2.0");
    if(callParams!=null) callObj.put("params", callParams);
    callObj.put("method", method);
    while(true) {
      // auto-retry because daemon calls fail sometimes
      try {
        return (JSONObject) HttpUtil.jsonCall(endpointUrlPrefix + "/json_rpc", callObj, false);
      }
      catch (Exception e) {
        System.out.println("Retrying daemon RPC call. Exception was: " + e.getCause().getMessage());
      }
    }
  }
  public JSONObject daemonOtherRpcCall(String method, JSONObject callParams) {
    while(true) {
      // auto-retry because daemon calls fail sometimes
      try {
        return (JSONObject) HttpUtil.jsonCall(endpointUrlPrefix + "/" + method, callParams, false);
      }
      catch (Exception e) {
        System.out.println("Retrying daemon RPC call. Exception was: " + e.getCause().getMessage());
      }
    }
  }

  public byte[] getTransactionBytes(String txHash) {

    JSONArray txHashes = new JSONArray();
    txHashes.put(txHash);

    JSONObject getTransactionsCallParams = new JSONObject();
    getTransactionsCallParams.put("txs_hashes", txHashes);
    JSONObject getTransactionsCallResult = daemonOtherRpcCall("gettransactions", getTransactionsCallParams);

    return ByteUtil.hexToBytes(getTransactionsCallResult.getJSONArray("txs_as_hex").getString(0));

  }

  public List<String> getTransactionHashesForBlock(int height) {
    JSONObject params = new JSONObject();
    params.put("height", height);
    JSONObject getBlockResult = daemonJsonRpcCall("getblock", params).getJSONObject("result");
    JSONArray txHashesArray = getBlockResult.optJSONArray("tx_hashes");
    List<String> txHashes = new ArrayList<>();
    if(txHashesArray!=null) {
      for (int i = 0; i < txHashesArray.length(); i++) txHashes.add(txHashesArray.getString(i));
    }
    return txHashes;
  }

  public int getLatestBlockHeight() {
    return daemonJsonRpcCall("get_info", new JSONObject()).getJSONObject("result").getInt("height");
  }

  public Transaction getTransactionForTxId(String txId) {
    return getTransactionsForTxIds(List.of(txId)).get(0);
  }

  public List<Transaction> getTransactionsForTxIds(List<String> txIds) {
    if(txIds.size()==0) return new ArrayList<>();
    JSONArray txIdsArray = new JSONArray();
    txIds.forEach(txid->txIdsArray.put(txid));
    JSONObject getTransactionsCallParams = new JSONObject();
    getTransactionsCallParams.put("txs_hashes", txIdsArray);
    JSONObject getTransactionsCallResult = daemonOtherRpcCall("get_transactions", getTransactionsCallParams);
    JSONArray txs = getTransactionsCallResult.getJSONArray("txs");

    List<Transaction> transactions = new ArrayList<>();
    for(int i=0; i<txs.length(); i++) {
      JSONObject tx = txs.getJSONObject(i);
      String hex = tx.getString("as_hex");
      if(hex.length()==0) hex = txs.getJSONObject(i).getString("pruned_as_hex");
      byte[] txBytes = ByteUtil.hexToBytes(hex);
      TransactionCursor txCursor = new TransactionCursor(txBytes);
      Transaction t = new Transaction();
      t.blockId = tx.getInt("block_height");
      t.version = (int) txCursor.readVarInt();
      long unlock_time = txCursor.readVarInt();
      t.inputRings = txCursor.readInputs();
      t.outputCount = (int) txCursor.readVarInt();
      t.id = txIdsArray.getString(i);
      transactions.add(t);
    }
    return transactions;
  }

  public Block getBlock(int height) throws Exception {
    JSONObject callParams = new JSONObject();
    callParams.put("height", height);
    JSONObject result = daemonJsonRpcCall("getblock", callParams).getJSONObject("result");
    return new Block(height, result);
  }

}
