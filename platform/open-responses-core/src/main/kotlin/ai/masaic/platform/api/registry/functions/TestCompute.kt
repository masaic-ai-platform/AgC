package ai.masaic.platform.api.registry.functions

val params =
    """
    {
        "data": {
            "person_type": [
                "Digital Enthusiasts", "Digital Enthusiasts", "Digital Enthusiasts",
                "Traditional Buyers", "Traditional Buyers", "Digital Enthusiasts",
                "Casual Users", "Casual Users", "Digital Enthusiasts"
            ],
            "AI_Assistant_Customer_Service_Willingness_to_Pay": [
                50, 60, 55, 30, 35, 70, 40, 45, None
            ],
            "AI_Assistant_Sales_Willingness_to_Pay": [
                45, 55, 50, 25, 30, 65, 38, 42, 48
            ]
        },
        "product": "AI_Assistant_Customer_Service",
        "segment_field": "person_type",
        "segment_value": "Digital Enthusiasts"
    }
    """.trimIndent()

fun buildWrappedCode(
    userFunName: String,
    userCode: String,
    jsonParams: String,
): String {
    val depsJson = emptyList<String>()
    return """
import sys, subprocess, json, base64
DEPS = $depsJson
for _pkg in DEPS:
    if _pkg:
        subprocess.run([sys.executable, "-m", "pip", "install", _pkg],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
params = $jsonParams
ENTRYPOINT = "$userFunName"
# === ANALYST CODE BEGINS ===
$userCode
# === ANALYST CODE ENDS   ===
if "run" not in globals():
    def run(params):
        fn = globals().get(ENTRYPOINT)
        if fn is None or not callable(fn):
            raise RuntimeError(f"ENTRYPOINT '{entrypoint}' not found as a function")
        return fn(**params)
def _safe_default(o):
    try: return o.__dict__
    except Exception: return str(o)
result = run(params)
print("<<RESULT_JSON>>" + json.dumps(result, default=_safe_default, ensure_ascii=False))
        """.trimIndent()
}

fun main() {
    val userCode =
        """
def get_willingness_to_pay_by_segment(
    df: pd.DataFrame,
    product: str,
    segment_field: str,
    segment_value: str
) -> Dict[str, Any]:
    ""${'"'}
    Get average willingness to pay for a product by customer segment.
    
    Args:
        df: Market research dataframe
        product: Product name (e.g., 'AI_Assistant_Customer_Service')
        segment_field: Segment field name (e.g., 'person_type')
        segment_value: Segment value (e.g., 'Digital Enthusiasts')
        
    Returns:
        Dictionary with willingness to pay statistics
    ""${'"'}
    # Filter data for the segment
    segment_data = df[df[segment_field] == segment_value]
    
    if segment_data.empty:
        return {
            "error": f"No data found for segment '{segment_value}' in field '{segment_field}'"
        }
    
    # Get willingness to pay column
    wtp_column = f"{product}_Willingness_to_Pay"
    
    if wtp_column not in df.columns:
        return {
            "error": f"Willingness to pay column '{wtp_column}' not found"
        }
    
    wtp_values = segment_data[wtp_column].dropna()
    
    if wtp_values.empty:
        return {
            "error": f"No willingness to pay data for {product} in segment {segment_value}"
        }
    
    return {
        "product": product,
        "segment_field": segment_field,
        "segment_value": segment_value,
        "count": len(wtp_values),
        "average_wtp": float(wtp_values.mean()),
        "median_wtp": float(wtp_values.median()),
        "std_wtp": float(wtp_values.std()),
        "min_wtp": float(wtp_values.min()),
        "max_wtp": float(wtp_values.max()),
        "distribution": wtp_values.value_counts().to_dict()
    }

        """.trimIndent()

    println(buildWrappedCode("get_willingness_to_pay_by_segment", userCode, params))
}
