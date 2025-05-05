package org.jetbrains.mcpserverplugin.mediaservices

import com.intellij.psi.*

class MessagePathExpressionVisitor : JavaRecursiveElementVisitor() {
    var constantValue: String = ""

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
        constantValue += expression.value.toString()
    }

    override fun visitBinaryExpression(expression: PsiBinaryExpression) {
        expression.lOperand.accept(this)
        val rOperand = expression.rOperand
        if (rOperand != null) {
            if (rOperand !is PsiLiteralExpression)
                constantValue += '{'
            rOperand.accept(this)
            if (rOperand !is PsiLiteralExpression)
                constantValue += '}'
        }
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        constantValue += "${expression.text}"
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        expression.resolve()?.let { resolved ->
            (resolved as? PsiField)?.computeConstantValue()?.let { value ->
                constantValue += value.toString()
            }
            (resolved as? PsiLocalVariable)?.let { identifier ->
                constantValue += "${identifier.name}"
            }
        }
    }
}
