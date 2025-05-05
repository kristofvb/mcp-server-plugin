class Test {

    public static final int BAR = 123;
    public static final int FOO = 456;

    void foo(String a, int b) {}

    public static void main() {
        new Test().<caret>foo("bar", getSomeConstant(), BAR + FOO);
    }

    private static int getSomeConstant() {
        return Constants.SOME_CONSTANT;
    }
}

class Constants {
    public static final int SOME_CONSTANT = 123;
}
