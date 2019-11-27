package jt;

public class NestedTest {
    public static void main(String[] args) {
        new Runnable(){
        
            @Override
            public void run() {
                System.out.println(args[0]);
            }
        };
    }
}