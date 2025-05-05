package org.jetbrains.mcpserverplugin.mediaservices

import com.google.gson.Gson
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.ide.mcp.MCPService
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool


private const val RABBITSENDER = "be.vrt.services.common.plugins.appregister.amqp.core.client.RabbitSender"

class FindMessageSenders : AbstractMcpTool<NoArgs>() {
    override val name: String = "find_message_senders"
    override val description: String = "Find all AMQP message senders"

    override fun handle(project: Project, args: NoArgs): Response {
        var gson = Gson()
        return runReadAction {
            val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
            val productionScope = GlobalSearchScopes.projectProductionScope(project)
            // assume RabbitSender interface lives in a library
            val librariesScope = ProjectScope.getLibrariesScope(project)
            val rabbitSenders = findClasses(project, librariesScope, RABBITSENDER)
            val result = ArrayList<MessageInteraction>()
            rabbitSenders
                .flatMap { sender -> sender.methods.toList() }
                .filterNotNull()
                .flatMap { method -> ReferencesSearch.search(method, productionScope).findAll() }
                .map { methodRef ->
                    MessageInteraction(
                        getOperation(methodRef),
                        getOperationPath(methodRef),
                        getModuleName(fileIndex, methodRef)
                    )
                }
                .forEach { interaction -> result.add(interaction) }

            logger<MCPService>().info("Found ${result}")
            Response(gson.toJson(result))
        }
    }

    private fun getOperation(methodRef: PsiReference): String {
        val methodCall = methodRef.element.parent as PsiMethodCallExpression
        val visitor = OperationVisitor()
        methodCall.accept(visitor)
        return visitor.operationName.orEmpty()
    }

    private class OperationVisitor : JavaElementVisitor() {
        var operationName: String? = null

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            expression.resolveMethod()?.let { method -> operationName = method.name }
        }
    }

    private fun getOperationPath(reference: PsiReference): String {
        val methodCall = reference.element.parent as PsiMethodCallExpression
        val visitor = PathVisitor()
        methodCall.accept(visitor)
        return visitor.pathValue.orEmpty()
    }

    private class PathVisitor : JavaElementVisitor() {
        var pathValue: String? = null

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            val args = expression.argumentList.expressions
            if (args.size > 1) {
                val path = args[1]
                val visitor = MessagePathExpressionVisitor()
                path.accept(visitor)
                pathValue = visitor.constantValue.toString()
            }
        }
    }

    private fun getModuleName(
        fileIndex: ProjectFileIndex,
        reference: PsiReference
    ): @NlsSafe String {
        var module = fileIndex.getModuleForFile(reference.element.containingFile.virtualFile)
        return module?.name?.substringBeforeLast('-').orEmpty()
    }

    private fun findClasses(project: Project, scope: GlobalSearchScope, qualifiedName: String): Array<PsiClass> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        return psiFacade.findClasses(qualifiedName, scope)
    }

    data class MessageInteraction(val operation: String, val path: String, val module: String) {
    }
}
