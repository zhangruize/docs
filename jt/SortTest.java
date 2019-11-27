package jt;

import java.util.Scanner;

public class SortTest {
    private static int count =0;
    public static void main(String[] args) {
        quickSort("8\n1 2");
        // quickSort("8\n8 7 6 5 4 3 2 1");
        // quickSort("8\n1 2 3 4 5 6 7 8");
        // quickSort("8\n1 3 41 5 23 2 11 1");
    }

    private static void quickSort(String input) {
        Scanner s = new Scanner(input);
        int n = s.nextInt();
        s.nextLine();
        int[] num = new int[n];
        for (int i = 0; i < n; i++) {
            num[i] = s.nextInt();
        }

        quickSort(num, 0, num.length - 1);

        for (int i = 0; i < n; i++) {
            System.out.print(num[i] + ", ");
        }
        System.out.println("\n"+count);
    }

    // 1, 3, 5, 2, 7
    private static void quickSort(int[] num, int left, int right) {
        count++;
        if (left < right) {
            // 选择一个基准，使得最后i左侧是小于该基准的数，右侧是大于该基准的数
            int l = left, r = right, x = num[left];
            while (l < r) {
                while (num[r] >= x && l < r) {
                    r--;
                }
                if (l < r) {
                    num[l++] = num[r];
                }
                while (num[l] <= x && l < r) {
                    l++;
                }
                if (l < r) {
                    num[r--] = num[l];
                }
            }
            num[l] = x;
            quickSort(num, left, l - 1);
            quickSort(num, l + 1, right);
        }
    }
}