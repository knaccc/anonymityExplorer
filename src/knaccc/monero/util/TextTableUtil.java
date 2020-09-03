package knaccc.monero.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TextTableUtil {

  public static String leftPad(String s, int len) {
    while(s.length()<len) s=" " + s;
    return s;
  }

  public final static DecimalFormat commaGrouped = new DecimalFormat("#,###");

  public static String printTable(String title, List<List<Object>> table) {
    int[] colMaxWidths = new int[table.get(0).size()];
    table = table.stream().map(row->row.stream().map(cell->cell instanceof Number ? commaGrouped.format(cell) : cell)
      .collect(Collectors.toList())).collect(Collectors.toList());
    for(int row=0; row<table.size(); row++) {
      for(int col=0; col<table.get(0).size(); col++) {
        colMaxWidths[col] = Math.max(colMaxWidths[col], table.get(row).get(col).toString().length());
      }
    }
    String horizLine = "=".repeat(Arrays.stream(colMaxWidths).sum() + colMaxWidths.length+(colMaxWidths.length-1)*3+1)+"\n";
    String s = horizLine + " " + title + "\n" + horizLine;
    table.add(1, Arrays.stream(colMaxWidths).mapToObj(i->"-".repeat(i+1)).collect(Collectors.toList()));
    for(int row=0; row<table.size(); row++) {
      for(int col=0; col<table.get(0).size(); col++) {
        s += leftPad(table.get(row).get(col)+"", colMaxWidths[col]+1);
        if(col!=table.get(0).size()-1) s+=row==1?"-|-":" | ";
      }
      if(row==1) s+="-";
      if(row!=table.size()-1) s+="\n";
    }
    return s+"\n"+horizLine;
  }

  public static String printFreqMapTable(String colTitle, Map<Integer, Integer> freqMap, boolean isCumulativeFreq) {
    int max = freqMap.keySet().stream().mapToInt(i->i).max().getAsInt();
    for(int i=0; i<max; i++) freqMap.putIfAbsent(i, 0);
    var table = createEmptyTable();
    List<Object> headerRow = new ArrayList<>();
    headerRow.add(colTitle);
    headerRow.add("freq");
    headerRow.add("%");
    table.add(headerRow);
    int sum = freqMap.values().stream().mapToInt(n->n).sum();
    for(int i=0; i<=max; i++) {
      final int finalI = i;
      List<Object> row = new ArrayList<>();
      row.add((isCumulativeFreq ? ">= " : "")  + commaGrouped.format(i));
      int count = freqMap.getOrDefault(i, 0);
      if(isCumulativeFreq) count = freqMap.entrySet().stream().filter(e->e.getKey()>=finalI).mapToInt(e->e.getValue()).sum();
      row.add(commaGrouped.format(count));
      String percentage = format1dp(100d * (((double) count) / ((double) sum)));
      row.add(percentage);
      table.add(row);
    }
    return printTable("", table);
  }


  public static String format1dp(double value)  {
    DecimalFormat df2dp = (DecimalFormat) NumberFormat.getNumberInstance();
    df2dp.applyPattern("0.0");
    return df2dp.format(value);
  }
  public static String format0dp(double value)  {
    DecimalFormat df2dp = (DecimalFormat) NumberFormat.getNumberInstance();
    df2dp.applyPattern("0");
    return df2dp.format(value);
  }

  public static List<List<Object>> createEmptyTable() {
    return new ArrayList<>();
  }

}
