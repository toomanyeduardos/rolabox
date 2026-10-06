package com.eduardoflores.rolabox.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseOrRepositoryClassInApiTest {
    private val rule = UseCaseOrRepositoryClassInApi(Config.empty)

    @Test
    fun `flags a use case and a repository declared as classes`() {
        val findings = rule.lint(
            """
            class SignInUseCase @Inject constructor(private val authRepository: AuthRepository) {
                suspend operator fun invoke(email: String, password: String) = Unit
            }

            abstract class AuthRepository

            internal data class CachedRepository(val name: String)
            """.trimIndent(),
        )

        assertEquals(3, findings.size)
        assertTrue(findings.all { it.message.startsWith("ADR-021 rule 1:") })
        assertEquals(1, findings.first().entity.location.source.line)
    }

    @Test
    fun `passes interfaces, models and errors`() {
        val findings = rule.lint(
            """
            interface SignInUseCase {
                suspend operator fun invoke(email: String, password: String)
            }

            fun interface AuthRepository {
                fun observeAuthState(): Flow<AuthState>
            }

            data class AuthUser(val id: String)

            sealed interface SignInError {
                data class Auth(val cause: AuthError) : SignInError
            }

            enum class StartDestination { AccessGranted, SignIn }

            object PasswordPolicy
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }

    @Test
    fun `passes a class that only has the word inside its name`() {
        val findings = rule.lint(
            """
            class RepositoryError
            class UseCaseResult
            """.trimIndent(),
        )

        assertEquals(emptyList<Any>(), findings)
    }
}
