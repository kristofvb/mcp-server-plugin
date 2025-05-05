package org.jetbrains.mcpserverplugin.mediaservices

import com.intellij.psi.PsiMethodCallExpression
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$PROJECT_ROOT/src/test/testData")
class MessagePathExpressionVisitorTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/psi"

    fun testPathAsConstantAndIdParameter() {
        val visitor = MessagePathExpressionVisitor()
        val ref = myFixture.getReferenceAtCaretPosition("ConstantAndId.java")
        val methodCall = ref?.element?.parent as PsiMethodCallExpression
        val pathArg = methodCall.argumentList.expressions[0]
        pathArg.accept(visitor)
        assertEquals("/some/path/{id}", visitor.constantValue)
    }

    fun testPathAsConstantAndMethodCallParameter() {
        val visitor = MessagePathExpressionVisitor()
        val ref = myFixture.getReferenceAtCaretPosition("ConstantAndMethodCall.java")
        val methodCall = ref?.element?.parent as PsiMethodCallExpression
        val pathArg = methodCall.argumentList.expressions[0]
        pathArg.accept(visitor)
        assertEquals("/some/path/{someMethod(id)}", visitor.constantValue)
    }
}
