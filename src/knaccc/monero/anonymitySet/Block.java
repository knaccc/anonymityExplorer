package knaccc.monero.anonymitySet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Block {
  
  public int height;
  public long timestamp;
  public JSONObject json;
  public JSONObject json2;
  public String minerTxHash;
  public Block(int height, JSONObject json) throws Exception {
    this.height = height;
    this.json = json;
    this.json2 = new JSONObject(json.getString("json"));
    this.timestamp = json2.getInt("timestamp")*1000L;
    this.minerTxHash = json.getJSONObject("block_header").getString("miner_tx_hash");
  }
  public int getBlockRewardOutputCount(boolean ringCtOnly) {
    JSONObject minerTx = json2.getJSONObject("miner_tx");
    int version = minerTx.getInt("version");
    if(ringCtOnly && version<2) return 0;
    return minerTx.getJSONArray("vout").length();
  }
  public List<String> getNonCoinbaseTxIds() {
    JSONArray nonCoinbaseBlockTxHashes = json2.getJSONArray("tx_hashes");
    ArrayList<String> result = new ArrayList<>();
    for(int i=0; i<nonCoinbaseBlockTxHashes.length(); i++) result.add(nonCoinbaseBlockTxHashes.getString(i));
    return result;
  }
}
