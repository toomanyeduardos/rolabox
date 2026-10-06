package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultViewModelInComposableTest {
    private val rule = DefaultViewModelInComposable(Config.empty)

    @Test
    fun `flags a composable that takes a concrete ViewModel`() {
        val findings = rule.lint(
            """
            @Composable
            internal fun SignInRoute(onSignedIn: () -> Unit, viewModel: DefaultSignInViewModel = hiltViewModel()) {
                SignInScreen(viewModel.uiState)
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.startsWith("ADR-021 rule 2:"))
        assertEquals(2, findings.single().entity.location.source.line)
    }

    @Test
    fun `flags a composable that creates a concrete ViewModel with hiltViewModel`() {
        val findings = rule.lint(
            """
            @Composable
            internal fun SignInRoute(onSignedIn: () -> Unit) {
                val viewModel = hiltViewModel<DefaultSignInViewModel>()
                SignInScreen(viewModel.uiState)
            }

            @Composable
            fun CreateAccountRoute() {
                val viewModel: DefaultCreateAccountViewModel = hiltViewModel()
                CreateAccountScreen(viewModel.uiState)
            }
            """.trimIndent(),
        )

        assertEquals(2, findings.size)
        assertEquals(listOf(3, 9), findings.map { it.entity.location.source.line })
    }

    @Test
    fun `passes a route that takes the abstract ViewModel`() {
        val findings = rule.lint(
            """
            @Composable
            internal fun SignInRoute(viewModel: SignInViewModel, onSignedIn: () -> Unit) {
                SignInScreen(viewModel.uiState)
            }

            @Composable
            internal fun OtherRoute(viewModel: OtherViewModel = hiltViewModel()) {
                val nested = hiltViewModel<NestedViewModel>()
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes the entry, which is where the concrete ViewModel is named`() {
        val findings = rule.lint(
            """
            internal class DefaultAuthUiEntries @Inject constructor() : AuthUiEntries {
                override fun appStackEntries(scope: EntryProviderScope<NavKey>, onSignedIn: () -> Unit) = with(scope) {
                    entry<SignInKey> {
                        SignInRoute(viewModel = hiltViewModel<DefaultSignInViewModel>(), onSignedIn = onSignedIn)
                    }
                }
            }

            class MainActivity : ComponentActivity() {
                private val viewModel: MainActivityViewModel by viewModels<DefaultMainActivityViewModel>()
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes the concrete ViewModel itself`() {
        val findings = rule.lint(
            """
            @HiltViewModel
            internal class DefaultSignInViewModel @Inject constructor(private val signIn: SignInUseCase) : SignInViewModel() {
                fun copy(other: DefaultSignInViewModel) = Unit
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
