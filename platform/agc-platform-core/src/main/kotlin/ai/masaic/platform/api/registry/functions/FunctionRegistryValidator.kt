package ai.masaic.platform.api.registry.functions

import java.util.regex.Pattern

/**
 * Validates function registry data according to the minimal specification.
 */
class FunctionRegistryValidator {
    companion object {
        private val NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$")
        private val PIP_REQUIREMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\[\\]-]+(?:[<>=!~]=?[a-zA-Z0-9._\\[\\]-]+)*(?:,[<>=!~]=?[a-zA-Z0-9._\\[\\]-]+)*$")
    }

    /**
     * Validates function name according to the spec pattern.
     */
    fun validateName(name: String): ValidationResult =
        when {
            name.isBlank() -> ValidationResult.Error(ErrorCodes.INVALID_NAME, "Function name cannot be blank")
            !NAME_PATTERN.matcher(name).matches() ->
                ValidationResult.Error(
                    ErrorCodes.INVALID_NAME,
                    "Function name must match pattern: ^[a-zA-Z_][a-zA-Z0-9_-]{2,64}$",
                )
            else -> ValidationResult.Success
        }

    /**
     * Validates pip requirement strings.
     */
    fun validateDeps(deps: List<String>): ValidationResult {
        val invalidDeps = deps.filter { !isValidPipRequirement(it) }
        return if (invalidDeps.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(
                ErrorCodes.INVALID_DEPS,
                "Invalid pip requirements: ${invalidDeps.joinToString(", ")}",
            )
        }
    }

    /**
     * Validates a complete function creation request.
     */
    fun validateCreateRequest(request: FunctionCreate): ValidationResult {
        val nameValidation = validateName(request.name)
        if (nameValidation is ValidationResult.Error) return nameValidation

        val depsValidation = validateDeps(request.deps)
        if (depsValidation is ValidationResult.Error) return depsValidation

        if (request.description.isBlank()) {
            return ValidationResult.Error("INVALID_DESCRIPTION", "Description cannot be blank")
        }

        return ValidationResult.Success
    }

    /**
     * Validates a function update request.
     */
    fun validateUpdateRequest(request: FunctionUpdate): ValidationResult {
        request.deps?.let { deps ->
            val depsValidation = validateDeps(deps)
            if (depsValidation is ValidationResult.Error) return depsValidation
        }

        request.description?.let { description ->
            if (description.isBlank()) {
                return ValidationResult.Error("INVALID_DESCRIPTION", "Description cannot be blank")
            }
        }

        return ValidationResult.Success
    }

    /**
     * Checks if a string is a valid pip requirement.
     */
    private fun isValidPipRequirement(requirement: String): Boolean = PIP_REQUIREMENT_PATTERN.matcher(requirement.trim()).matches()
}

/**
 * Result of validation operations.
 */
sealed class ValidationResult {
    object Success : ValidationResult()

    data class Error(
        val code: String,
        val message: String,
    ) : ValidationResult()
}
