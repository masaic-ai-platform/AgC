package ai.masaic.platform.api.registry.functions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionRegistryValidatorTest {
    private val validator = FunctionRegistryValidator()

    @Test
    fun `validateName should accept valid names`() {
        val validNames =
            listOf(
                "valid_name",
                "validName",
                "valid_name_123",
                "VALID_NAME",
                "a",
                "ab",
                "a" + "b".repeat(63),
            )

        validNames.forEach { name ->
            val result = validator.validateName(name)
            assertTrue(result is ValidationResult.Success, "Name '$name' should be valid")
        }
    }

    @Test
    fun `validateName should reject invalid names`() {
        val invalidNames =
            listOf(
                "",
                " ",
                "123name",
                "-name",
                "name with spaces",
                "name@symbol",
                "name/slash",
            )

        invalidNames.forEach { name ->
            val result = validator.validateName(name)
            assertTrue(result is ValidationResult.Error, "Name '$name' should be invalid")
            if (result is ValidationResult.Error) {
                assertEquals(ErrorCodes.INVALID_NAME, result.code)
            }
        }
    }

    @Test
    fun `validateDeps should accept valid pip requirements`() {
        val validDeps =
            listOf(
                "pandas==2.2.2",
                "numpy>=1.21.0",
                "requests",
                "pydantic~=2.0.0",
                "fastapi[all]",
                "torch>=2.0.0,<3.0.0",
            )

        validDeps.forEach { dep ->
            val result = validator.validateDeps(listOf(dep))
            assertTrue(result is ValidationResult.Success, "Dependency '$dep' should be valid")
        }
    }

    @Test
    fun `validateDeps should reject invalid pip requirements`() {
        val invalidDeps =
            listOf(
                "",
                " ",
                "package with spaces",
                "package@invalid",
                "package/slash",
                "package\\backslash",
            )

        invalidDeps.forEach { dep ->
            val result = validator.validateDeps(listOf(dep))
            assertTrue(result is ValidationResult.Error, "Dependency '$dep' should be invalid")
            if (result is ValidationResult.Error) {
                assertEquals(ErrorCodes.INVALID_DEPS, result.code)
            }
        }
    }

    @Test
    fun `validateDeps should reject empty list`() {
        val result = validator.validateDeps(emptyList())
        assertTrue(result is ValidationResult.Error)
        if (result is ValidationResult.Error) {
            assertEquals(ErrorCodes.INVALID_DEPS, result.code)
        }
    }

    @Test
    fun `validateCode should accept valid Python code`() {
        val validCode =
            """
            def run(params):
                return {"result": "success"}
            """.trimIndent()

        val result = validator.validateCode(validCode)
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validateCode should reject invalid Python code`() {
        val invalidCodes =
            listOf(
                "",
                " ",
                "print('hello')", // Missing run function
                "def hello(): pass", // Wrong function name
            )

        invalidCodes.forEach { code ->
            val result = validator.validateCode(code)
            assertTrue(result is ValidationResult.Error, "Code should be invalid: $code")
        }
    }

    @Test
    fun `validateCreateRequest should accept valid request`() {
        val validRequest =
            FunctionCreate(
                name = "valid_function",
                description = "A valid function description",
                deps = listOf("pandas==2.2.2"),
                code =
                    """
                    def run(params):
                        return {"result": "success"}
                    """.trimIndent(),
            )

        val result = validator.validateCreateRequest(validRequest)
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validateCreateRequest should reject invalid request`() {
        val invalidRequest =
            FunctionCreate(
                name = "123invalid",
                description = "A valid function description",
                deps = listOf("pandas==2.2.2"),
                code =
                    """
                    def run(params):
                        return {"result": "success"}
                    """.trimIndent(),
            )

        val result = validator.validateCreateRequest(invalidRequest)
        assertTrue(result is ValidationResult.Error)
        if (result is ValidationResult.Error) {
            assertEquals(ErrorCodes.INVALID_NAME, result.code)
        }
    }
}
