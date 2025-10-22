package ai.masaic.openresponses.api.utils

import ai.masaic.openresponses.api.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SupportedToolsFormatterTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `test MCP tool parsing - Allbirds example`() {
        val allBirdsFunMcp =
            """
            {
                "type": "function",
                "function": {
                    "name": "allbirds_mcp_tool_action_YWxsYmlyZH",
                    "description": "Allbirds MCP server tool",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "type": {
                                "type": "string",
                                "enum": [
                                    "mcp"
                                ]
                            },
                            "server_label": {
                                "type": "string",
                                "enum": [
                                    "allbirds"
                                ]
                            },
                            "server_url": {
                                "type": "string",
                                "enum": [
                                    "https://allbirds.com/api/mcp"
                                ]
                            },
                            "allowed_tools": {
                                "type": "array",
                                "items": {
                                    "type": "string",
                                    "enum": [
                                        "search_shop_catalog",
                                        "get_cart",
                                        "update_cart",
                                        "search_shop_policies_and_faqs",
                                        "get_product_details"
                                    ]
                                }
                            }
                        },
                        "required": [
                            "type",
                            "server_label",
                            "server_url",
                            "allowed_tools"
                        ],
                        "additionalProperties": false
                    }
                }
            }
            """.trimIndent()
        
        val mcpTool = SupportedToolsFormatter.buildToolDef(mapper.readValue(allBirdsFunMcp))
        
        // Assertions for MCP tool
        assertNotNull(mcpTool, "MCP tool should not be null")
        assertTrue(mcpTool is MCPTool, "Tool should be an instance of MCPTool")
        
        val mcpToolInstance = mcpTool as MCPTool
        assertEquals("mcp", mcpToolInstance.type, "Tool type should be 'mcp'")
        assertEquals("allbirds", mcpToolInstance.serverLabel, "Server label should be 'allbirds'")
        assertEquals("https://allbirds.com/api/mcp", mcpToolInstance.serverUrl, "Server URL should match")
        assertTrue(mcpToolInstance.allowedTools.isNotEmpty(), "Allowed tools should not be empty")
        assertTrue(mcpToolInstance.allowedTools.contains("search_shop_catalog"), "Should contain search_shop_catalog tool")
    }

    @Test
    fun `test MCP tool parsing - Box server with authentication`() {
        val mcpFunJsonWithAuth =
            """
        {
    "type": "function",
    "function": {
        "description": "Get Box MCP server definition",
        "name": "post_mcpdefinition_action_bWNwLmJveC",
        "parameters": {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "enum": [
                        "mcp"
                    ]
                },
                "server_label": {
                    "type": "string",
                    "enum": [
                        "box-server"
                    ]
                },
                "server_url": {
                    "type": "string",
                    "enum": [
                        "https://mcp.box.com"
                    ]
                },
                "allowed_tools": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "enum": [
                            "who_am_i",
                            "search_folders_by_name",
                            "list_folder_content_by_folder_id",
                            "search_files_keyword",
                            "get_file_content",
                            "ai_qa_single_file",
                            "ai_qa_multi_file",
                            "ai_qa_hub",
                            "ai_extract_freeform"
                        ]
                    }
                },
                "headers": {
                    "type": "object",
                    "properties": {
                        "Authorization": {
                            "type": "string",
                            "enum": [
                                "Bearer {apikey}"
                            ],
                            "description": "Fixed Authorization bearer token."
                        }
                    },
                    "required": [
                        "Authorization"
                    ],
                    "additionalProperties": false
                }
            },
            "required": [
                "type",
                "server_label",
                "server_url",
                "allowed_tools",
                "headers"
            ],
            "additionalProperties": false
        },
        "strict": true
    }
}
            """.trimIndent()
        
        val mcpTool = SupportedToolsFormatter.buildToolDef(mapper.readValue(mcpFunJsonWithAuth))
        
        // Assertions for MCP tool with authentication
        assertNotNull(mcpTool, "MCP tool should not be null")
        assertTrue(mcpTool is MCPTool, "Tool should be an instance of MCPTool")
        
        val mcpToolInstance = mcpTool as MCPTool
        assertEquals("mcp", mcpToolInstance.type, "Tool type should be 'mcp'")
        assertEquals("box-server", mcpToolInstance.serverLabel, "Server label should be 'box-server'")
        assertEquals("https://mcp.box.com", mcpToolInstance.serverUrl, "Server URL should match")
        assertTrue(mcpToolInstance.allowedTools.isNotEmpty(), "Allowed tools should not be empty")
        assertTrue(mcpToolInstance.allowedTools.contains("who_am_i"), "Should contain who_am_i tool")
        assertTrue(mcpToolInstance.headers.containsKey("Authorization"), "Should contain Authorization header")
        assertEquals("Bearer {apikey}", mcpToolInstance.headers["Authorization"], "Authorization header should match")
    }

    @Test
    fun `test file search tool parsing`() {
        val fileSearchToolFunJson =
            """
            {
              "type": "function",
              "function": {
                "name": "file-search-tool",
                "description": "This tool can make provide information about chemical reactions",
                "strict": true,
                "parameters": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "file_search"
                      ]
                    },
                    "vector_store_ids": {
                      "type": "array",
                      "items": {
                        "type": "string",
                        "enum": [
                          "vs_68b98d857f73cf000000"
                        ]
                      }
                    },
                    "modelInfo": {
                      "type": "object",
                      "properties": {
                        "bearerToken": {
                          "type": "string",
                          "enum": [
                            "{bearer token.....}"
                          ]
                        },
                        "model": {
                          "type": "string",
                          "enum": [
                            "openai@text-embedding-3-small"
                          ]
                        }
                      },
                      "required": [
                        "bearerToken",
                        "model"
                      ],
                      "additionalProperties": false
                    }
                  },
                  "required": [
                    "type",
                    "vector_store_ids",
                    "modelInfo"
                  ],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
        
        val fileSearchTool = SupportedToolsFormatter.buildToolDef(mapper.readValue(fileSearchToolFunJson))
        
        // Assertions for file search tool
        assertNotNull(fileSearchTool, "File search tool should not be null")
        assertTrue(fileSearchTool is FileSearchTool, "Tool should be an instance of FileSearchTool")
        
        val fileSearchToolInstance = fileSearchTool as FileSearchTool
        assertEquals("file_search", fileSearchToolInstance.type, "Tool type should be 'file_search'")
        assertEquals("file-search-tool", fileSearchToolInstance.alias, "Tool alias should match")
        assertEquals("This tool can make provide information about chemical reactions", fileSearchToolInstance.description, "Tool description should match")
        assertTrue(fileSearchToolInstance.vectorStoreIds?.isNotEmpty() == true, "Vector store IDs should not be empty")
        assertTrue(fileSearchToolInstance.vectorStoreIds?.contains("vs_68b98d857f73cf000000") == true, "Should contain expected vector store ID")
        assertNotNull(fileSearchToolInstance.modelInfo, "Model info should not be null")
        assertEquals("openai@text-embedding-3-small", fileSearchToolInstance.modelInfo?.model, "Model should match")
        assertEquals("{bearer token.....}", fileSearchToolInstance.modelInfo?.bearerToken, "Bearer token should match")
    }

    @Test
    fun `test Python function tool parsing`() {
        val pyFunToolJson =
            """
            {
              "type": "function",
              "function": {
                "name": "python-function-tool",
                "strict": true,
                "parameters": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "py_fun_tool"
                      ]
                    },
                    "tool_def": {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string",
                          "enum": [
                            "discount_calculator_macro"
                          ]
                        },
                        "description": {
                          "type": "string",
                          "enum": [
                            "Applies a discount to a price and returns the calculation details as a dictionary."
                          ]
                        },
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "price": {
                              "type": "number"
                            },
                            "discount_pct": {
                              "type": "number"
                            }
                          },
                          "required": [
                            "price",
                            "discount_pct"
                          ],
                          "additionalProperties": false
                        }
                      },
                      "required": [
                        "name",
                        "description"
                      ],
                      "additionalProperties": false
                    },
                    "code": {
                      "type": "string",
                      "enum": [
                        "ZGVmIGRpc2NvdW50X2NhbGN1bGF0b3JfbWFjcm8ocHJpY2U6IGZsb2F0LCBkaXNjb3VudF9wY3Q6IGZsb2F0KSAtPiBkaWN0OgogICAgIiIiCiAgICBBcHBseSBhIGRpc2NvdW50IHRvIGEgc2luZ2xlIHByaWNlIGFuZCByZXR1cm4gcmVzdWx0IGFzIGFuIG9iamVjdC4KCiAgICBBcmdzOgogICAgICAgIHByaWNlIChmbG9hdCk6IE9yaWdpbmFsIHByaWNlCiAgICAgICAgZGlzY291bnRfcGN0IChmbG9hdCk6IERpc2NvdW50IHBlcmNlbnRhZ2UgKGUuZy4sIDIwIGZvciAyMCUpCgogICAgUmV0dXJuczoKICAgICAgICBkaWN0OiB7CiAgICAgICAgICAgICJvcmlnaW5hbF9wcmljZSI6IDxmbG9hdD4sCiAgICAgICAgICAgICJkaXNjb3VudF9wY3QiOiA8ZmxvYXQ+LAogICAgICAgICAgICAiZGlzY291bnRlZF9wcmljZSI6IDxmbG9hdD4KICAgICAgICB9CiAgICAiIiIKICAgIGRpc2NvdW50ZWRfcHJpY2UgPSBwcmljZSAqICgxIC0gZGlzY291bnRfcGN0IC8gMTAwLjApCiAgICByZXR1cm4gewogICAgICAgICJvcmlnaW5hbF9wcmljZSI6IHByaWNlLAogICAgICAgICJkaXNjb3VudF9wY3QiOiBkaXNjb3VudF9wY3QsCiAgICAgICAgImRpc2NvdW50ZWRfcHJpY2UiOiByb3VuZChkaXNjb3VudGVkX3ByaWNlLCAyKQogICAgfQ=="
                      ]
                    },
                    "code_interpreter": {
                      "type": "object",
                      "properties": {
                        "server_label": {
                          "type": "string",
                          "enum": [
                            "e2b-server"
                          ]
                        },
                        "url": {
                          "type": "string",
                          "enum": [
                            "http://localhost:8000/mcp"
                          ]
                        },
                        "apiKey": {
                          "type": "string",
                          "enum": [
                            "{api_key}"
                          ]
                        }
                      },
                      "required": [
                        "server_label",
                        "url",
                        "apiKey"
                      ],
                      "additionalProperties": false
                    }
                  },
                  "required": [
                    "type",
                    "code",
                    "code_interpreter",
                    "tool_def"
                  ],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
        
        val pyFunTool = SupportedToolsFormatter.buildToolDef(mapper.readValue(pyFunToolJson))
        
        // Assertions for Python function tool
        assertNotNull(pyFunTool, "Python function tool should not be null")
        assertTrue(pyFunTool is PyFunTool, "Tool should be an instance of PyFunTool")
        
        val pyFunToolInstance = pyFunTool as PyFunTool
        assertEquals("py_fun_tool", pyFunToolInstance.type, "Tool type should be 'py_fun_tool'")
        assertNotNull(pyFunToolInstance.functionDetails, "Function details should not be null")
        assertEquals("discount_calculator_macro", pyFunToolInstance.functionDetails.name, "Function name should match")
        assertEquals("Applies a discount to a price and returns the calculation details as a dictionary.", pyFunToolInstance.functionDetails.description, "Function description should match")
        assertTrue(pyFunToolInstance.functionDetails.parameters.isNotEmpty())
        assertNotNull(pyFunToolInstance.code, "Code should not be null")
        assertTrue(pyFunToolInstance.code.isNotEmpty(), "Code should not be empty")
        assertNotNull(pyFunToolInstance.interpreterServer, "Interpreter server should not be null")
        assertEquals("e2b-server", pyFunToolInstance.interpreterServer?.serverLabel, "Server label should match")
        assertEquals("http://localhost:8000/mcp", pyFunToolInstance.interpreterServer?.url, "Server URL should match")
        assertEquals("{api_key}", pyFunToolInstance.interpreterServer?.apiKey, "API key should match")
    }
}
