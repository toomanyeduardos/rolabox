import com.eduardoflores.rolabox.buildlogic.ModuleGraphTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

class RolaboxModuleGraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val task = target.tasks.register<ModuleGraphTask>("moduleGraph") {
            group = "documentation"
            description = "Writes the module dependency graph into README.md"
            readme.set(target.layout.projectDirectory.file("README.md"))
        }

        target.gradle.projectsEvaluated {
            val modules = target.subprojects
                .filter { it.buildFile.exists() }
                .map { it.path }
                .sorted()
            val edges = target.subprojects.flatMap { module ->
                module.configurations
                    .filter { it.name in GRAPH_CONFIGURATIONS }
                    .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                    .map { "${module.path} -> ${it.path}" }
            }.distinct().sorted()

            task.configure {
                this.modules.set(modules)
                this.edges.set(edges)
            }
        }
    }

    private companion object {
        val GRAPH_CONFIGURATIONS = setOf("api", "implementation")
    }
}
