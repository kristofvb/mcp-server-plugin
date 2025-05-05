import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase


@TestDataPath("\$PROJECT_ROOT/src/test/testData")
class PsiTest : BasePlatformTestCase() {

    fun testPsiArgument() {
        val methodRef = myFixture.getReferenceAtCaretPosition("Test.java")
        val methodCall = methodRef?.element?.parent as PsiMethodCallExpression
        val args = methodCall.argumentList.expressions
        println(getOperation(methodCall))
        println(args.joinToString(", ") { getValue(it) })
    }

    fun testAnnotation() {
        val annotationRef = myFixture.getReferenceAtCaretPosition("AnnotationTest.java")
        val annotationElement = annotationRef?.element?.parent as PsiAnnotation
        val args = annotationElement.parameterList.attributes
        val pathValue = args[0].value
        println(getValue(pathValue as PsiExpression))
    }

    private fun getValue(expression: PsiExpression): String {
        val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage())
        if (expressionEvaluator != null) {
            var constantExpression = expressionEvaluator.computeConstantExpression(expression, false)
            return constantExpression?.toString() ?: expression.text
        } else {
            return expression.text
        }
    }

    private fun getOperation(call: PsiMethodCallExpression): String {
        var methodVisitor = MethodNameVisitor()
        call.accept(methodVisitor)
        return methodVisitor.operation ?: ""
    }

    class MethodNameVisitor : JavaRecursiveElementVisitor() {
        var operation: String? = null

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            expression.resolveMethod()?.let { method -> operation = method.name }
        }
    }

    override fun getTestDataPath() = "src/test/testData/psi"

}
