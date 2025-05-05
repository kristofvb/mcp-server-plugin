package org.jetbrains.mcpserverplugin.mediaservices

import com.google.gson.Gson
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.stream
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool


private const val GATEWAY_ANNOTATION = "be.vrt.services.common.api.spring.annotation.Gateway"

class FindGateways : AbstractMcpTool<NoArgs>() {
    override val name: String = "find_gateways"
    override val description: String = "Find all gateways"

    override fun handle(project: Project, args: NoArgs): Response {
        return runReadAction {
            val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
            val productionScope = GlobalSearchScopes.projectProductionScope(project)
            // assume the annotation lives in a library
            val librariesScope = ProjectScope.getLibrariesScope(project)
            val gateways = findClasses(project, librariesScope, GATEWAY_ANNOTATION)
            val result = gateways
                .stream()
                .flatMap { gateway -> ReferencesSearch.search(gateway, productionScope).findAll().stream() }
                .filter { reference -> reference.element.parent is PsiAnnotation }
                .map { Gateway(getSystemValue(it), getModuleName(fileIndex, it), getOperations(it)) }
                .toList()

            Response(Gson().toJson(result))
        }
    }

    private fun getSystemValue(annotationRef: PsiReference): String {
        val annotation = annotationRef.element.parent as PsiAnnotation
        val args = annotation.parameterList.attributes
        val system = args[0].value as? PsiExpression ?: return ""
        val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(system.getLanguage())
        val constantExpression = expressionEvaluator?.computeConstantExpression(system, false)
        return constantExpression?.toString() ?: system.text.orEmpty()
    }

    private fun getOperations(annotationRef: PsiReference): List<String> {
        val owner = PsiTreeUtil.getParentOfType<PsiClass?>(annotationRef.element, PsiClass::class.java);
        var methods = listOf<String>()
        if (owner is PsiClass) {
            methods = owner.methods.stream()
                .filter { method -> method.modifierList.hasModifierProperty(PsiModifier.PUBLIC) }
                .filter { method -> !method.isConstructor }
                .map { it.name }
                .toList()
        }
        return methods
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

    data class Gateway(val system: String, val module: String, val operations: List<String>) {
    }

}
