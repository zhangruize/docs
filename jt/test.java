package jt;

class Test {

    int num = 0;

    public static void main(String[] args) {
        Test t = new Test();
        t.num=1;
        Test tx = new Test();
        tx.num=2;
        Test t2 = t.createOne(tx);
        Test t3 = createOneStatic();
        System.out.print("hhh");
    }

    public Test createOne(Test origin) {
        Test t = new Test();
        t.num = origin.num + 1;
        return t;
    }

    public static Test createOneStatic() {
        return new Test();
    }
}