package knaccc.monero.anonymitySet.tasks;

import java.util.Arrays;

// from https://stackoverflow.com/questions/2799078/permutation-algorithm-without-recursion-java
public class PermutationUtil<T> {

  private T[] arr;
  private int[] permSwappings;

  public PermutationUtil(T[] arr) {
    this(arr, arr.length);
  }

  public PermutationUtil(T[] arr, int permSize) {
    this.arr = arr.clone();
    this.permSwappings = new int[permSize];
    for (int i = 0; i < permSwappings.length; i++)
      permSwappings[i] = i;
  }

  public T[] next() {
    if (arr == null)
      return null;

    T[] res = Arrays.copyOf(arr, permSwappings.length);
    //Prepare next
    int i = permSwappings.length - 1;
    while (i >= 0 && permSwappings[i] == arr.length - 1) {
      swap(i, permSwappings[i]); //Undo the swap represented by permSwappings[i]
      permSwappings[i] = i;
      i--;
    }

    if (i < 0)
      arr = null;
    else {
      int prev = permSwappings[i];
      swap(i, prev);
      int next = prev + 1;
      permSwappings[i] = next;
      swap(i, next);
    }

    return res;
  }

  private void swap(int i, int j) {
    T tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }

  public static void main(String[] args) {
    var p = new PermutationUtil<Long>(new Long[]{1L, 2L, 3L});
    Long[] a;
    while((a=p.next())!=null) {
      System.out.println(Arrays.asList(a));
    }
  }

}
