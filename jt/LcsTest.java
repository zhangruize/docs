package jt;

import java.util.Scanner;

public class LcsTest {

    public static void main(String[] args) {
        assertResult(lcs("a\na"), 1);
        assertResult(lcs("abcdef\nabcdef"), 6);
        assertResult(lcs("abcdef\nabcdefgjklwe"), 6);
        assertResult(lcs("abcdeffasegseg\nabcdef"), 6);
        assertResult(lcs(""), 0);
        assertResult(lcs(null), 0);
        assertResult(lcs("abc\n"), 0);
        assertResult(lcs("\n"), 0);
        assertResult(lcs("\nsfs"), 0);
        assertResult(lcs("abcdef\nkkblde00"), 3);
    }

    private static void assertResult(int result, int expected) {
        if (result != expected) {
            throw new AssertionError("wrong answer "+ result+" is not " + expected);
        }
    }

    private static int lcs(String input) {
        if(input == null || input.length() <=0){
            return 0;
        }
        Scanner s = new Scanner(input);
        String sa = null, sb = null;
        try{
        sa = s.nextLine();
        sb = s.nextLine();
        }catch(Exception e){return 0;}

        int[][] dp = new int[sa.length()+1][sb.length()+1];
        // lcs(i,j) =
        for (int i = 1; i <= sa.length(); i++) {
            for (int j = 1; j <= sb.length(); j++) {
                if (sa.charAt(i-1) == sb.charAt(j-1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[sa.length()][sb.length()];
    }
}