package org.jetbrains.mcpserverplugin.mediaservices

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.containers.stream
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

private const val HANDLER_ANNOTATION = "be.vrt.services.common.plugins.dispatcher.amqp.core.annotation.AmqpRequestMapping"

class FindMessageReceivers : AbstractMcpTool<NoArgs>() {
    override val name: String = "find_message_receivers"
    override val description: String = "Find all AMQP message receivers"

    override fun handle(project: Project, args: NoArgs): Response {
        return runReadAction {
            val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
            val productionScope = GlobalSearchScopes.projectProductionScope(project)
            // assume the handler annotation lives in a library
            val librariesScope = ProjectScope.getLibrariesScope(project)
            val messageHandlers = findClasses(project, librariesScope, HANDLER_ANNOTATION)
            val result = messageHandlers
                .stream()
                .flatMap { handler -> ReferencesSearch.search(handler, productionScope).findAll().stream() }
                .filter { reference -> reference.element.parent is PsiAnnotation }
                .map { """{ "path": "${getPathValue(it)}", "module": "${getModuleName(fileIndex, it)}" }""" }
                .toList()

            Response(result.joinToString(",\n", prefix = "[", postfix = "]"))
        }
    }

    private fun getPathValue(annotationRef: PsiReference): String {
        val annotationElement = annotationRef.element.parent as PsiAnnotation
        val args = annotationElement.parameterList.attributes
        val pathValue = args[0].value as? PsiExpression ?: return ""
        val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(pathValue.getLanguage())
        val constantExpression = expressionEvaluator?.computeConstantExpression(pathValue, false)
        return constantExpression?.toString() ?: pathValue.text.orEmpty()
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

}
