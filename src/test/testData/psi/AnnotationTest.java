class AnnotationTest {
    public static final String PATH = "/v1/videos/{videoId}/trailer";

    @<caret>AmqpRequestMapping(path = AnnotationTest.PATH, method = PUT)
    void foo() {}

}
