class Test {

    public static void main() {
        String id = "123";
        new Test().<caret>foo("/some/path/" + id);
    }

    void foo(String s) {}
}
