package knaccc.monero.anonymityExplorer;

import knaccc.monero.anonymityExplorer.tasks.PermutationUtil;
import knaccc.monero.daemonRpc.DaemonRpc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.stream.Collectors;

import static knaccc.monero.util.ByteUtil.*;
import static knaccc.monero.anonymityExplorer.Cursor.readVarInt;
import static knaccc.monero.anonymityExplorer.Cursor.toVarInt;
import static knaccc.monero.util.TextTableUtil.commaGrouped;

public class AnonymityExplorer {

  /* data format:
    varint recordType            // 0 = block record, 1 = tx record

    recordType==0:
    varint blockHeight           // height of the block about to be listed
    varint preBlockOutputHeight  // height of the output id prior to the outputs contained within the block
    varint len                   // length of the data until the next block record

    recordType==1:
    varint outputCount           // how many outputs does this record apply to? (each set of possible inputs will apply to all outputs in each tx)
    varint len                   // how many bytes is this record, excluding outputCount and this len field
                                 // if len==0, stop reading here. tx is non-ringCT
    varint inputRingCount            // how many input rings are there. (if the answer is zero (because it's a non-ringCT tx, the record ends here)
    varint inputsPerRingCount         // how many inputs are there per ring. assumes all rings must be the same size
    {[varint] inputIdOffsets}    // for each input ring:
                                 //   the absolute id of the first ringCT input, followed by relative offsets of the subsequent ringCT input ids (non-ringCT inputs ignored)
                                 // (id means the sequential numbering of ringCT output ids as they appear in the blockchain)
                                 
  */

  /*
    index is list of uint32s (i.e. 4 bytes each) pointing to recordType==0 entries in the data
    thus position 4*i points to the record for the block containing output id i*16
   */

  // first ringCT output is the miner tx output in block 1220516. RingCT transacations have tx version >= 2
  final int ringCTActivationHeight = 1220516;
  int blockHeight = ringCTActivationHeight-1; // only index from ringCT blocks onwards
  long outputHeight = -1;
  File dataFile;
  FileChannel dataFileChannel;
  RandomAccessFile dataFileRw;
  MappedByteBuffer dataBuffer;
  public ByteBuffer index;

  public int getLastStoredBlockHeight() {
    return blockHeight;
  }

  public final DaemonRpc daemonRpc;


  public AnonymityExplorer(File storageDir, String daemonEndpoint) throws Exception {

    daemonRpc = new DaemonRpc(daemonEndpoint);
    System.out.println("Connected to node version " + daemonRpc.daemonJsonRpcCall("get_info", null).getJSONObject("result").getString("version") + " at " + daemonEndpoint);

    if(!storageDir.exists()) storageDir.mkdir();

    dataFile = new File(storageDir, "data.bin");

    dataFileRw = new RandomAccessFile(dataFile, "rw");
    dataFileChannel = dataFileRw.getChannel();

    int fileSize = (int) dataFile.length();
    dataBuffer = dataFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
    dataBuffer.limit(fileSize);
    scanData();
  }

  private final static ByteBuffer tmp = ByteBuffer.allocate(1*1024*1024);

  private void writeRecordType0(ByteBuffer buf, int blockHeight, long preBlockOutputHeight, int len) {
    if(preBlockOutputHeight==-1) preBlockOutputHeight=0; // we can't store negative numbers, so write zero instead and treat it as a special case
    buf.put(toVarInt(0));
    buf.put(toVarInt(blockHeight));
    buf.put(toVarInt(preBlockOutputHeight));
    buf.put(toVarInt(len));
  }

  private void writeRecordType1(ByteBuffer buf, int outputCount) {
    writeRecordType1(buf, outputCount, null);
  }
  private void writeRecordType1(ByteBuffer buf, Transaction t) {
    writeRecordType1(buf, t.outputCount, t);
  }

  private void writeRecordType1(ByteBuffer buf, int outputCount, Transaction t) {
    buf.put(toVarInt(1)); // recordType 1

    if(t==null) {
      // this is the block miner tx. write the output count, and that's all
      buf.put(toVarInt(outputCount));
      buf.put(toVarInt(0));
    }
    else if(!t.isRingCt()) {
      // a non-ringCt transaction, so no ringCT outputs to write
      buf.put(toVarInt(0)); // outputCount=0 because non-ringCT tx
      buf.put(toVarInt(0)); // len=0. ignore this non-ringCT tx and end the record here
    }
    else {
      buf.put(toVarInt(outputCount)); // outputCount
      List<byte[]> data = new ArrayList<>();
      data.add(toVarInt(t.inputRings.size())); // inputRingCount
      if(t.inputRings.size()>0) {
        int inputsPerRingCount = t.inputRings.get(0).keyOffsets.length;
        data.add(toVarInt(inputsPerRingCount)); // inputsPerRingCount
        t.inputRings.forEach(inputRing->{
          if(inputRing.keyOffsets.length!=inputsPerRingCount) throw new RuntimeException("Mismatched inputsPerRingCount");
          Arrays.stream(inputRing.keyOffsets).forEach(keyOffset->{
            data.add(toVarInt(keyOffset));
          });
        });
        int len = data.stream().mapToInt(b -> b.length).sum();
        buf.put(toVarInt(len));
        data.forEach(buf::put);
      }
      else {
        throw new RuntimeException("This should never happen");
      }
    }
  }

  public void addBlock(Block block) {
    blockHeight = block.height;

    long preBlockOutputHeight = outputHeight;

    tmp.clear();
    int blockRewardOutputs = block.getBlockRewardOutputCount(true);
    outputHeight+=blockRewardOutputs;
    writeRecordType1(tmp, blockRewardOutputs);
    for(Transaction t : daemonRpc.getTransactionsForTxIds(block.getNonCoinbaseTxIds())) {
      writeRecordType1(tmp, t);
      if(t.isRingCt()) outputHeight+=t.outputCount;
    }
    writeRecordType0(dataBuffer, blockHeight, preBlockOutputHeight, tmp.position());
    tmp.flip();
    dataBuffer.put(tmp);
    tmp.limit(tmp.capacity());

  }

  public static class BlockchainStats {
    public Map<Integer, Integer> txPerBlockFreq = new HashMap<>();
    public Map<Integer, Integer> inputRingMembersPerBlockFreq = new HashMap<>();
    public Map<Integer, Integer> outputsPerBlockFreq = new HashMap<>();
    public Map<Integer, Integer> outputsPerTxFreq = new HashMap<>();
    public Map<Integer, Integer> inputRingMembersPerTxFreq = new HashMap<>();
    public Map<Integer, Integer> inputRingsPerTxFreq = new HashMap<>();
  }
  public BlockchainStats getBlockchainStats(int startBlockId) {
    BlockchainStats stats = new BlockchainStats();

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    int currentBlockOutputCount = -1;
    int currentBlockTxCount = -1;
    int currentBlockInputCount = -1;
    int currentBlockId = -1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        if(currentBlockInputCount!=-1 && (currentBlockId>=startBlockId)) {
          // these will apply to the results from the examination of the prior block
          stats.inputRingMembersPerBlockFreq.merge(currentBlockInputCount, 1, Integer::sum);
          stats.outputsPerBlockFreq.merge(currentBlockOutputCount, 1, Integer::sum);
          stats.txPerBlockFreq.merge(currentBlockTxCount, 1, Integer::sum);
        }
        currentBlockInputCount = 0;
        currentBlockOutputCount = 0;
        currentBlockTxCount = 0;
        currentBlockId = (int) readVarInt(buf);
        long preBlockOutputHeight = readVarInt(buf);
        int len = (int) readVarInt(buf); // read len field
        if(currentBlockId<startBlockId) buf.position(buf.position()+len); // skip to end of block

      }
      else if(recordType == 1) {
        int outputCount = (int) readVarInt(buf);
        stats.outputsPerTxFreq.merge(outputCount, 1, Integer::sum);
        currentBlockOutputCount += outputCount;
        currentBlockTxCount++;
        int len = (int) readVarInt(buf); // len

        int recordStartPos = buf.position();

        int totalInputRingMembers = 0;
        int inputRingCount = 0;
        if(len!=0) {
          inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount
          totalInputRingMembers = inputRingCount*inputsPerRingCount;
        }

        buf.position(recordStartPos+len);

        currentBlockInputCount+=totalInputRingMembers;
        stats.inputRingMembersPerTxFreq.merge(totalInputRingMembers, 1, Integer::sum);
        stats.inputRingsPerTxFreq.merge(inputRingCount, 1, Integer::sum);
      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }
    return stats;
  }

  public int[] getOutputRefCounts() {
    int[] outputIdUses = new int[(int) outputHeight+1]; // if/when outputs exceed 2 billion, will need a custom data structure

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        readVarInt(buf); readVarInt(buf); readVarInt(buf); // skip over blockHeight, preBlockOutputHeight, len fields
      }
      else if(recordType == 1) {
        int outputCount = (int) readVarInt(buf);
        int len = (int) readVarInt(buf);

        if(len!=0) {
          int inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount

          for(int i=0; i<inputRingCount; i++) {
            long inputId=0;
            for(int j=0; j<inputsPerRingCount; j++) {
              inputId += readVarInt(buf);
              outputIdUses[(int)inputId]++;
            }
          }
        }

      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }

    return outputIdUses;
  }
  public int[] getMaxBackwardChainLengths() {
    int[] maxBackwardChainLengths = new int[(int) outputHeight+1]; // if/when outputs exceed 2 billion, will need a custom data structure

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    int currentOutputHeight=-1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        readVarInt(buf); // skip over blockHeight field
        currentOutputHeight = (int) readVarInt(buf)+1;
        readVarInt(buf); // skip over len field
      }
      else if(recordType == 1) {
        int outputCount = (int) readVarInt(buf);
        int outputIdStartInclusive = currentOutputHeight;
        int outputIdEndExclusive = outputIdStartInclusive+outputCount;
        currentOutputHeight+=outputCount;

        int len = (int) readVarInt(buf); // outputCount
        if(len!=0) {
          int inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount
          int m = -1;
          for(int i=0; i<inputRingCount; i++) {
            long inputId=0;
            for(int j=0; j<inputsPerRingCount; j++) {
              inputId += readVarInt(buf);
              m = Math.max(m, maxBackwardChainLengths[(int) inputId]);
            }
          }
          for(int i=outputIdStartInclusive; i<outputIdEndExclusive; i++) maxBackwardChainLengths[i] = m+1;
        }

      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }

    return maxBackwardChainLengths;
  }


  // read through the data to determine the latest block stored and get stats
  public void scanData() throws Exception {
    System.out.println("Scanning existing data...");
    long startMs = new Date().getTime();
    dataBuffer.position(0);
    int lastBlockPos = -1;
    while(dataBuffer.hasRemaining()) {
      lastBlockPos = dataBuffer.position();
      int recordType = dataBuffer.get();
      if(recordType==0) {
        blockHeight = (int) readVarInt(dataBuffer);
        outputHeight = (int) readVarInt(dataBuffer);
        if(blockHeight<=ringCTActivationHeight) outputHeight=-1; // special case, since we're unable to store a negative number
        int len = (int) readVarInt(dataBuffer);
        dataBuffer.position(dataBuffer.position()+len); // skip to end of block
      }
      else {
        throw new RuntimeException("Record type 0 expected. Found record type: " + recordType);
      }
    }
    if(lastBlockPos!=-1) {
      // read the last block found, to ensure the outputHeight includes outputs encountered in that last block
      dataBuffer.position(lastBlockPos);

      // skip over block record (recordType==0) to get to the tx records
      readVarInt(dataBuffer); // skip over recordType
      readVarInt(dataBuffer); // skip over blockHeight
      readVarInt(dataBuffer); // skip over preBlockOutputHeight
      readVarInt(dataBuffer); // skip over len

      while(dataBuffer.hasRemaining()) {
        int recordType = dataBuffer.get();
        if(recordType!=1) throw new RuntimeException("Record type 0 expected. Found record type: " + recordType);
        int outputCount = (int) readVarInt(dataBuffer);
        outputHeight += outputCount;
        int len = (int) readVarInt(dataBuffer);
        dataBuffer.position(dataBuffer.position()+len); // skip to next record
      }
    }

    System.out.println("Existing data scan completed, duration: " + ((new Date().getTime() - startMs)/1000) + "s");
    System.out.println("Scanned up to block height: " + commaGrouped.format(blockHeight) + ", RingCT output height: " + commaGrouped.format(outputHeight));

  }

  public void rebuildIndex() {

    if(index==null) index = ByteBuffer.allocateDirect(128*1024*1024);

    System.out.println("Rebuilding index...");
    long startMs = new Date().getTime();
    index.clear();
    int currentOutputHeight = 0;
    int i = 0;
    int increment = 16;

    index.put(longToUint32Bytes(0));
    i+=increment;

    ByteBuffer buf = dataBuffer.duplicate();
    buf.flip();
    int blockRecordPos=-1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType==0) {
        blockRecordPos = buf.position()-1;
        readVarInt(buf); // skip over the blockHeight
        readVarInt(buf); // skip over the preBlockOutputHeight
        readVarInt(buf); // skip over the len varint
      }
      else if(recordType==1) {
        int outputCount = (int) readVarInt(buf); // outputCount
        currentOutputHeight+=outputCount;

        while(i<=currentOutputHeight) {
          index.put(longToUint32Bytes(blockRecordPos));
          if(dataBuffer.get(blockRecordPos)!=0) throw new RuntimeException("Assert failed " + i + " blockRecordPos: " + blockRecordPos);
          i+=increment;
        }

        int len = (int) readVarInt(buf); // len
        buf.position(buf.position()+len); // skip over inputs to end of record
      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType);
      }
    }
    index.limit(index.position());
    System.out.println("Index rebuilt, duration: " + ((new Date().getTime() - startMs)/1000) + "s");
  }

  private int getBlockRecordPosForOutputId(long outputId) {
    byte[] uint32Pos = new byte[4];
    if(index==null) rebuildIndex();
    index.get(4*((int) outputId/16), uint32Pos, 0, 4);
    int blockRecordPos = (int) uint32BytesToLong(uint32Pos);
    return blockRecordPos;
  }

  public long getMinOutputIdForBlockId(int blockId) {
    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if (recordType == 0) {
        var thisBlockId = (int) readVarInt(buf); // skip blockHeight
        var thisOutputHeight = readVarInt(buf); // set currentOutputHeight to preBlockOutputHeight
        if(thisBlockId==blockId) {
          return thisOutputHeight;
        }
        int len = (int) readVarInt(buf);
        buf.position(buf.position()+len);
      } else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }
    throw new RuntimeException("Block not found");
  }

  public List<Long> getOutputIdsForTxId(String txId) {

    var blockId = daemonRpc.getTransactionForTxId(txId).blockId;

    List<String> txIdsInBlock = daemonRpc.getTransactionHashesForBlock(blockId);
    var txIndexInBlock = -1;
    for(int i=0; i<txIdsInBlock.size(); i++) if(txIdsInBlock.get(i).equals(txId)) txIndexInBlock = i;
    if(txIndexInBlock==-1) throw new RuntimeException("This should not happen");
    var r=  lookupOutputIds(blockId, txIndexInBlock);
    List<Long> outputIds = new ArrayList<>();
    for(long i=r.txOutputIdStartInclusive; i<r.txOutputIdEndExclusive; i++) outputIds.add(i);
    return outputIds;
  }


  public List<Integer> getAnonymitySetForTxId(String txId, int levels, int observationWindowDays) {

    Transaction tx = daemonRpc.getTransactionForTxId(txId);

    long minOutputId = observationWindowDays<=0 ? 0 : getMinOutputIdForBlockId(tx.blockId - observationWindowDays * 24 * 30);

    List<InputRing> inputRings = tx.inputRings;
    Set<Long> anonymitySetOutputIds = new HashSet<>();
    inputRings.stream().filter(InputRing::isRingCt).forEach(i->anonymitySetOutputIds.addAll(i.getRingInputIds(minOutputId)));
    List<Integer> result = new ArrayList<>();

    while(levels>1) {
      Map<Long, Integer> outputIdToLevelsExploredMap = new HashMap<>();
      levels--;
      final int finalLevels = levels;
      Set<Long> subLevelAnonymitySetOutputIds = new HashSet<>();
      anonymitySetOutputIds.forEach(i->{
        Integer existing = outputIdToLevelsExploredMap.get(i);
        if(existing!=null && existing>=finalLevels) {
          // already explored
        }
        else {
          subLevelAnonymitySetOutputIds.addAll(getAnonymitySetForOutputId(i, finalLevels, outputIdToLevelsExploredMap, minOutputId));
          outputIdToLevelsExploredMap.put(i, finalLevels);
        }
      });
      result.add(subLevelAnonymitySetOutputIds.size());
    }

    result.add(anonymitySetOutputIds.size());
    Collections.reverse(result);

    return result;
  }

  // levels=0 means just return the specified output, don't look at its inputs at all
  public Set<Long> getAnonymitySetForOutputId(long outputId, int levels, Map<Long, Integer> outputIdToLevelsExploredMap, long minOutputId) {
    if(levels==0) return Set.of(outputId);
    var thisLevel = getTxRecord(outputId).getAllInputIds();
    thisLevel.add(outputId);
    thisLevel = thisLevel.stream().filter(i->i>=minOutputId).collect(Collectors.toSet());
    var subLevels = new HashSet<Long>();
    if(levels>1) {
      thisLevel.forEach(i->{
        Integer existing = outputIdToLevelsExploredMap.get(i);
        if(existing!=null && existing>=levels) {
          // already explored
        }
        else {
          subLevels.addAll(getAnonymitySetForOutputId(i, levels-1, outputIdToLevelsExploredMap, minOutputId));
          outputIdToLevelsExploredMap.put(i, levels);
        }
      });
    }
    thisLevel.addAll(subLevels);
    outputIdToLevelsExploredMap.put(outputId, levels);
    return thisLevel;
  }

  public void printPaths(Set<Long> srcOutputs, Set<Long> destOutputs, int maxLevels) {
    for(long destOut : destOutputs) {
      printPaths(new ArrayList<>(), srcOutputs, destOut, maxLevels);
    }
  }

  private String getOutputBlockTxIdKeyString(long destOutput) {
    return "  block: " + lookupBlockIdForOutputId(destOutput) + ", txid: " + lookupTxIdForOutputId(destOutput) + " output id: " + destOutput + " key: " + getOutputPubKeyForOutputIdViaDaemon(destOutput);
  }

  // dest is later output, src are earlier outputs that could have lead to dest
  public void printPaths(List<Long> path, Set<Long> srcOutputs, long destOutput, int maxLevels) {

    if(srcOutputs.contains(destOutput)) {
      String s = lookupBlockIdForOutputId(destOutput)+"";
      if(path.size()==0) {
        System.out.println("Direct path for output " + destOutput + " via block: " + s);
        System.out.println(getOutputBlockTxIdKeyString(destOutput));
      }
      else {
        for (int i = path.size() - 1; i >= 0; i--) {
          s+="->"+lookupBlockIdForOutputId(path.get(i));
        }
        System.out.println("Level " + path.size() + " path from output " + destOutput + " to " + path.get(0) + " via blocks: " + s);
        System.out.println(getOutputBlockTxIdKeyString(destOutput));
        for (int i = path.size() - 1; i >= 0; i--) {
          long id = path.get(i);
          System.out.println(getOutputBlockTxIdKeyString(id));
        }
      }
    }
    if(maxLevels>0) {
      var inputs = getTxRecord(destOutput).getAllInputIds();
      for(long input : inputs) {
        List<Long> path2 = new ArrayList<>(path);
        path2.add(destOutput);
        printPaths(path2, srcOutputs, input, maxLevels-1);
      }
    }
  }


  public void readLatestBlocks() throws Exception {

    System.out.println("Reading latest blocks from daemon...");

    int existingLimit = dataBuffer.limit();
    // expand buffer to 1GB. May need to raise this in future as the blockchain grows
    dataBuffer = dataFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.MAX_VALUE/2);
    dataBuffer.position(existingLimit);

    // due to possible blockchain reorgs, always be 10 blocks behind the node's block height
    int endHeight = daemonRpc.daemonOtherRpcCall("getheight", new JSONObject()).getInt("height") - 10;

    long startMs = new Date().getTime();
    for(int height=blockHeight+1; height<endHeight; height++) {
      if(((endHeight-height)<1000) || height%100==0) System.out.println("Reading block height: " + commaGrouped.format(height) + " remaining: " + commaGrouped.format(endHeight-height));
      Block block = daemonRpc.getBlock(height);
      addBlock(block);
    }
    rebuildIndex();
    dataBuffer.limit(dataBuffer.position());
    dataFileRw.setLength(dataBuffer.limit()); // contract the file to only the data stored, not the entire 1GB buffer capacity
    System.out.println("Data size: " + commaGrouped.format(dataBuffer.position()) + " bytes");
    System.out.println("Index size: " + commaGrouped.format(index.position()) + " bytes");
    System.out.println("Daemon read duration: " + (new Date().getTime()-startMs)/1000 + "s");

  }

  public static class LookupOutputIdsResult {
    public long txOutputIdStartInclusive;
    public long txOutputIdEndExclusive;
  }
  public LookupOutputIdsResult lookupOutputIds(int blockId, int txIndexWithinBlock) {

    LookupOutputIdsResult result = new LookupOutputIdsResult();
    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    int currentOutputHeight=-1;
    int currentBlockId = -1;
    int currentTxIndexWithinBlock = 0;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        currentBlockId = (int) readVarInt(buf); // blockHeight field
        currentOutputHeight = (int) readVarInt(buf)+1; // what the id of the first output in the block will be
        int len = (int) readVarInt(buf); // read len field
        if(currentBlockId!=blockId) buf.position(buf.position()+len); // skip to end of block
      }
      else if(recordType == 1) {
        // we only arrive here if we're in the correct block
        int outputCount = (int) readVarInt(buf); // outputCount
        int outputIdStartInclusive = currentOutputHeight;
        int outputIdEndExclusive = outputIdStartInclusive+outputCount;
        currentOutputHeight+=outputCount;

        if(currentTxIndexWithinBlock==txIndexWithinBlock) {
          result.txOutputIdStartInclusive = outputIdStartInclusive;
          result.txOutputIdEndExclusive = outputIdEndExclusive;
          return result;
        }

        int len = (int) readVarInt(buf); // len
        buf.position(buf.position()+len); // skip to end of tx record

        currentTxIndexWithinBlock++;
      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }
    throw new RuntimeException("Could not locate output id");

  }

  public long getTxCountFromBlockOnwards(int startBlock) {
    long c=0;

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(0);
    int currentOutputHeight=-1;
    int currentBlockId = -1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        currentBlockId = (int) readVarInt(buf); // blockHeight field
        currentOutputHeight = (int) readVarInt(buf)+1; // what the id of the first output in the block will be
        int len = (int) readVarInt(buf); // read len field
        if(currentBlockId<startBlock) buf.position(buf.position()+len); // skip to end of block
      }
      else if(recordType == 1) {
        // we only arrive here if we're in the starting block or later
        int outputCount = (int) readVarInt(buf);
        int len = (int) readVarInt(buf);
        buf.position(buf.position()+len); // skip to end of record
        currentOutputHeight+=outputCount;
        c++;
      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }
    return c;
  }

  public Set<Long> getForwardOutputIdRefs(long outputId, int levels, int maxBlockDistance) {

    Map<Long, Integer> forwardOutputIdToLevelMap = new HashMap<>();

    forwardOutputIdToLevelMap.put(outputId, 0);

    if(levels==0) return forwardOutputIdToLevelMap.keySet();

    TxRecord txRecord = getTxRecord(outputId);

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(getBlockRecordPosForOutputId(outputId));
    int currentOutputHeight=-1;
    int currentBlockId = -1;
    int txIndexWithinBlock = -1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        txIndexWithinBlock = 0;
        currentBlockId = (int) readVarInt(buf); // blockHeight field
        currentOutputHeight = (int) readVarInt(buf)+1; // what the id of the first output in the block will be
        int len = (int) readVarInt(buf); // read len field
        if(currentBlockId<txRecord.block) buf.position(buf.position()+len); // skip to end of block
        if(currentBlockId>(txRecord.block+maxBlockDistance)) break;
      }
      else if(recordType == 1) {
        // we only arrive here if we're in the starting block or later
        int outputCount = (int) readVarInt(buf); // outputCount

        int len = (int) readVarInt(buf); // len
        if(len!=0) {
          int inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount

          for (int i = 0; i < inputRingCount; i++) {
            long inputId = 0;
            for (int j = 0; j < inputsPerRingCount; j++) {
              inputId += readVarInt(buf);
              if (forwardOutputIdToLevelMap.containsKey(inputId)) {
                int existingLevel = forwardOutputIdToLevelMap.get(inputId);
                if((existingLevel+1)<=levels) {
                  for (long k = 0; k < outputCount; k++) {
                    forwardOutputIdToLevelMap.put(currentOutputHeight + k, existingLevel+1);
                  }
                }
              }
            }
          }
        }
        currentOutputHeight+=outputCount;
        txIndexWithinBlock++;

      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }

    return forwardOutputIdToLevelMap.keySet();

  }


  public Set<TxRecord> getForwardTxRecordRefs(long outputId, int blockDistance) {

    Set<TxRecord> result = new HashSet<>();

    Set<Long> anonSet = new HashSet<>();
    TxRecord txRecord = getTxRecord(outputId);

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(getBlockRecordPosForOutputId(outputId));
    int currentOutputHeight=-1;
    int currentBlockId = -1;
    int txIndexWithinBlock = -1;
    while(buf.hasRemaining()) {
      int recordType = buf.get();
      if(recordType == 0) {
        txIndexWithinBlock = 0;
        currentBlockId = (int) readVarInt(buf); // blockHeight field
        currentOutputHeight = (int) readVarInt(buf)+1; // what the id of the first output in the block will be
        int len = (int) readVarInt(buf); // read len field
        if(currentBlockId<txRecord.block) buf.position(buf.position()+len); // skip to end of block
        if(currentBlockId>(txRecord.block+blockDistance)) break;
      }
      else if(recordType == 1) {
        // we only arrive here if we're in the starting block or later
        int outputCount = (int) readVarInt(buf); // outputCount

        int len = (int) readVarInt(buf); // len
        if(len!=0) {
          int inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount

          for (int i = 0; i < inputRingCount; i++) {
            long inputId = 0;
            for (int j = 0; j < inputsPerRingCount; j++) {
              inputId += readVarInt(buf);
              if (outputId == inputId || anonSet.contains(inputId)) {
                for (long k = 0; k < outputCount; k++) anonSet.add(currentOutputHeight + k);

                TxRecord txr = new TxRecord();
                txr.block = currentBlockId;
                txr.txIndexWithinBlock = txIndexWithinBlock;
                result.add(txr);

              }
            }
          }
        }
        currentOutputHeight+=outputCount;
        txIndexWithinBlock++;

      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position() - 1));
      }
    }

    return result;

  }


  Map<Long, String> outputIdToPubKeyCache = new HashMap<>();
  public String getOutputPubKeyForOutputIdViaDaemon(long outputId) {
    if(!outputIdToPubKeyCache.containsKey(outputId)) {
      JSONArray outputs = new JSONArray();
      JSONObject output = new JSONObject();
      output.put("amount", 0);
      output.put("index", "" + outputId);
      outputs.put(output);
      JSONObject params = new JSONObject();
      params.put("get_txid", true);
      params.put("outputs", outputs);
      JSONObject result = daemonRpc.daemonOtherRpcCall("get_outs", params);
      outputIdToPubKeyCache.put(outputId, result.getJSONArray("outs").getJSONObject(0).getString("key"));
    }
    return outputIdToPubKeyCache.get(outputId);
  }


  public String lookupTxIdForOutputId(long outputId) {
    try {
      TxRecord txRecord = getTxRecord(outputId);
      return lookupTxId(txRecord);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  public String lookupTxId(TxRecord txRecord) {
    return lookupTxId(txRecord.block, txRecord.txIndexWithinBlock);
  }
  public String lookupTxId(int blockId, int txIndexWithinBlock) {
    try {
      Block block = daemonRpc.getBlock(blockId);
      if (txIndexWithinBlock == 0) {
        // it's the miner tx
        return block.minerTxHash; // because the first tx in the block in our records is the miner tx
      } else {
        return daemonRpc.getTransactionsForTxIds(block.getNonCoinbaseTxIds()).get(txIndexWithinBlock - 1).id; // -1 because the first tx in the block in our records is the miner tx
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  public int lookupBlockIdForOutputId(long outputId) {
    try {
      TxRecord txRecord = getTxRecord(outputId);
      Block block = daemonRpc.getBlock(txRecord.block);
      return block.height;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static class TxRecord {
    public int block;
    public int outputCount;
    public long outputIdStart;
    public int txIndexWithinBlock = 0;
    public Set<Set<Long>> inputRings; // set of set of input ids
    public Set<Long> getAllInputIds() {
      Set<Long> allInputIds = new HashSet<>();
      if(inputRings!=null) inputRings.forEach(r->allInputIds.addAll(r));
      return allInputIds;
    }

    @Override
    public boolean equals(Object obj) {
      var t = (TxRecord) obj;
      return(t.block==block && t.txIndexWithinBlock==txIndexWithinBlock);
    }

    @Override
    public int hashCode() {
      return block + txIndexWithinBlock;
    }
  }

  public TxRecord getTxRecord(long outputId) {

    int blockRecordPos = getBlockRecordPosForOutputId(outputId);
    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(blockRecordPos);

    long currentOutputHeight=-1;
    TxRecord txRecord = new TxRecord();
    while(true) {
      int recordType = buf.get();
      if(recordType==0) {
        txRecord.block = (int) readVarInt(buf); // skip blockHeight
        txRecord.txIndexWithinBlock = 0;
        currentOutputHeight = readVarInt(buf); // set currentOutputHeight to preBlockOutputHeight
        if(txRecord.block<=ringCTActivationHeight) currentOutputHeight=-1; // special case for first output
        readVarInt(buf); // skip len
      }
      else if(recordType==1) {

        int outputCount = (int) readVarInt(buf); // outputCount
        if(currentOutputHeight<outputId && outputId<=(currentOutputHeight+outputCount)) {
          int len = (int) readVarInt(buf); // len

          txRecord.outputCount = outputCount;
          txRecord.outputIdStart = currentOutputHeight;

          if(len!=0) {
            int inputRingCount = (int) readVarInt(buf); // inputRingCount
            int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount

            txRecord.inputRings = new HashSet<>();
            for(int i=0; i<inputRingCount; i++) {
              var ring = new HashSet<Long>();
              long inputId = 0;
              for (int j = 0; j < inputsPerRingCount; j++) {
                inputId += readVarInt(buf);
                ring.add(inputId);
              }
              txRecord.inputRings.add(ring);
            }
          }

          return txRecord;
        }
        else {
          currentOutputHeight += outputCount;
          int len = (int) readVarInt(buf);
          buf.position(buf.position()+len);
        }

        txRecord.txIndexWithinBlock++;

      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position()-1));
      }
    }

  }




  public long getOutputHeight() {
    return outputHeight;
  }

  Map<Long, String> outputIdToTxIdCache = new HashMap<>();
  public String getTxIdForOutputIdViaDaemon(long outputId) {
    if(!outputIdToTxIdCache.containsKey(outputId)) {
      JSONArray outputs = new JSONArray();
      JSONObject output = new JSONObject();
      output.put("amount", 0);
      output.put("index", ""+outputId);
      outputs.put(output);
      JSONObject params = new JSONObject();
      params.put("get_txid", true);
      params.put("outputs", outputs);
      JSONObject result = daemonRpc.daemonOtherRpcCall("get_outs", params);
      outputIdToTxIdCache.put(outputId, result.getJSONArray("outs").getJSONObject(0).getString("txid"));
    }
    return outputIdToTxIdCache.get(outputId);
  }

  public static Set<Long> getRandomIdSetBetween(int n, long start, long end) {
    Set<Long> r = new HashSet<>();
    while(r.size()<n) r.add(getRandomIdBetween(start, end));
    return r;
  }
  public static long getRandomIdBetween(long start, long end) {
    return (long) ((Math.random()*(end-start))+start);
  }

  public List<String> getTxIdsWhereOutputSetsSpentTogether(Set<Set<Long>> outputIdSets) {

    List<String> matchingTxIds = new ArrayList<>();

    Set<Long> flattenedOutputIdSets = outputIdSets.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    long earliestOutputId = flattenedOutputIdSets.stream().mapToLong(i->i).min().getAsLong();
    int startBlockPos = getBlockRecordPosForOutputId(earliestOutputId);

    ByteBuffer buf = dataBuffer.duplicate();
    buf.position(startBlockPos);
    List<Set<Long>> ringMatchesList = new ArrayList<>();

    while(buf.hasRemaining()) {
      int recordType = buf.get();
      int txIndexWithinBlock = 0;
      int block = 0;
      if(recordType==0) {
        block = (int) readVarInt(buf); // skip blockHeight
        txIndexWithinBlock = 0;
        readVarInt(buf); // skip currentOutputHeight
        readVarInt(buf); // skip len
      }
      else if(recordType==1) {
        ringMatchesList.clear();
        int outputCount = (int) readVarInt(buf); // outputCount
        int len = (int) readVarInt(buf); // len
        if(len!=0) {
          int inputRingCount = (int) readVarInt(buf); // inputRingCount
          int inputsPerRingCount = (int) readVarInt(buf); // inputsPerRingCount
          for(int i=0; i<inputRingCount; i++) {
            Set<Long> ringMatches = new HashSet<>();
            long inputId = 0;
            for (int j = 0; j < inputsPerRingCount; j++) {
              inputId += readVarInt(buf);
              if(flattenedOutputIdSets.contains(inputId)) {
                ringMatches.add(inputId);
              }
            }
            if(ringMatches.size()>0) {
              ringMatchesList.add(ringMatches);
            }
          }
        }
        if(ringMatchesList.size()>=outputIdSets.size()) {

          var p = new PermutationUtil<Set<Long>>(outputIdSets.toArray(new Set[]{}));
          Set<Long>[] a;
          outer:for(int j=0; j<=(ringMatchesList.size()-outputIdSets.size()); j++) {
            while((a=p.next())!=null) {
              boolean allFound = true;
              for(int i=0; i<a.length; i++) {
                if(Collections.disjoint(ringMatchesList.get(i+j), a[i])) {
                  allFound = false;
                  break;
                }
              }
              if(allFound) {
                matchingTxIds.add(lookupTxId(block, txIndexWithinBlock));
                break outer;
              }
            }
          }
        }
        txIndexWithinBlock++;
      }
      else {
        throw new RuntimeException("Unexpected record type: " + recordType + " at pos: " + (buf.position()-1));
      }
    }
    return matchingTxIds;

  }

}
