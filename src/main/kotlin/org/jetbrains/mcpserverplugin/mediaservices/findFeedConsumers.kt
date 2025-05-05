package org.jetbrains.mcpserverplugin.mediaservices

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.ide.mcp.MCPService
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool

private const val FEEDCONSUMER = "be.vrt.services.common.plugins.feedreader.core.api.FeedConsumer"

class FindFeedConsumers : AbstractMcpTool<NoArgs>() {
    override val name: String = "find_feed_consumers"
    override val description: String = "Find all feed consumers"

    override fun handle(project: Project, args: NoArgs): Response {
        return runReadAction {
            val productionScope = GlobalSearchScopes.projectProductionScope(project)
            // assume FeedConsumer interface lives in a library
            val librariesScope = ProjectScope.getLibrariesScope(project)
            val baseClasses = findClasses(project, librariesScope, FEEDCONSUMER)
            logger<MCPService>().info("Found ${baseClasses.joinToString()} for interface $FEEDCONSUMER")
            val implementations = findImplementationsOf(project, baseClasses)
            val result = mutableListOf<String>()
            for (implementation in implementations) {
                val feedConsumer = findClass(project, productionScope, implementation)
                if (feedConsumer != null) {
                    feedConsumer.annotations.find { annotation ->
                        annotation.qualifiedName == "be.vrt.services.common.api.plugins.feedreader.Feed"
                    }?.let {
                        val feedName = getFeedName(it)
                        val sourceModule = getSourceModule(feedName)
                        result.add(
                            """
                            {
                                "name": "${feedConsumer.qualifiedName}",
                                "label": $feedName,
                                "source_module": $sourceModule,
                            }
                            """.trimIndent()
                        )
                    }
                }
            }
            Response(result.joinToString(",\n", prefix = "[", postfix = "]"))
        }
    }

    private fun getFeedName(annotation: PsiAnnotation): @NlsSafe String =
        (annotation.findAttributeValue("value") as? PsiLiteralExpression)?.getValue() as String

    private fun getSourceModule(feedName: String): String {
        return when (feedName) {
            "PUBLICATION", "PLANNED_PUBLICATIONS" -> "publishing"
            "DISTRIBUTED_VIDEO", "VIDEO" -> "video"
            "EPISODE", "CATALOG_SEASONS", "CATALOG_EPISODES", "CATALOG_SERIES" -> "catalog"
            "IMAGE" -> "image"
            else -> ""
        }
    }

    private fun findClasses(project: Project, scope: GlobalSearchScope, qualifiedName: String): Array<PsiClass> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        return psiFacade.findClasses(qualifiedName, scope)
    }

    private fun findClass(project: Project, scope: GlobalSearchScope, qualifiedName: String): PsiClass? {
        val psiFacade = JavaPsiFacade.getInstance(project)
        return psiFacade.findClass(qualifiedName, scope)
    }

    private fun findImplementationsOf(
        project: Project,
        baseClasses: Array<PsiClass>
    ): List<String> {
        val result = mutableListOf<String>()
        val scope = GlobalSearchScopes.projectProductionScope(project)

        for (baseClass in baseClasses) {
            val query = ClassInheritorsSearch.search(
                baseClass,
                scope,
                true  // Include indirect inheritors
            )

            val allInheritors = query.findAll()
            for (inheritor in allInheritors) {
                inheritor.qualifiedName?.let { result.add(it) }
            }
        }

        return result
    }
}
