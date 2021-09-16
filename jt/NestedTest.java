package jt;

public class NestedTest {
    Object b;

    public void main(String[] args) {

        new Runnable(){

            @Override
            public void run() {
                System.out.println(b);
                System.out.println(args[0]);
            }
        };
    }
}   