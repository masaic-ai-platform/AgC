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
                inputSchema = mutableMapOf(),
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
                inputSchema = mutableMapOf(),
            )

        val result = validator.validateCreateRequest(invalidRequest)
        assertTrue(result is ValidationResult.Error)
        if (result is ValidationResult.Error) {
            assertEquals(ErrorCodes.INVALID_NAME, result.code)
        }
    }
}
