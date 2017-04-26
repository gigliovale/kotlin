// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages

public class JavaClass {
    public void <caret>invoke() {
    }

    public static JavaClass INSTANCE = new JavaClass();

    public static class Other extends JavaClass {}
    public static class AnotherOther extends Other {}

    public static class JavaOther {
        public void invoke() {
        }

        public static JavaOther INSTANCE = new JavaOther();
    }

    public static class OtherJavaClass extends JavaClass {
        public static OtherJavaClass OJC = new OtherJavaClass();
    }
}