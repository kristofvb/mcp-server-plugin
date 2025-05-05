class Test {

    public static void main() {
        val test = new Test();
        val id = null;
        test.<caret>foo("/some/path/" + someMethod(id));
    }

    void foo(String s) {}

    String someMethod() {
        return "123";
    }
}
