package com.petcare.ai.agent.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolCallParser} 单测。
 */
class ToolCallParserTest {

    @Nested
    @DisplayName("解析有效工具调用")
    class ValidParses {

        @Test
        @DisplayName("带参数的工具调用")
        void parseWithArgs() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst(
                    "让我查一下 [TOOL:getProductInfo(productId=123)]");
            assertTrue(call.isPresent());
            assertEquals("getProductInfo", call.get().toolName());
            assertEquals("123", call.get().get("productId"));
            assertEquals("[TOOL:getProductInfo(productId=123)]", call.get().rawText());
        }

        @Test
        @DisplayName("无参数的工具调用")
        void parseNoArgs() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst(
                    "我帮你查门店 [TOOL:getStoreInfo()]");
            assertTrue(call.isPresent());
            assertEquals("getStoreInfo", call.get().toolName());
            assertTrue(call.get().raw().isEmpty());
        }

        @Test
        @DisplayName("多个参数")
        void parseMultiArgs() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst(
                    "[TOOL:searchOrders(userId=42, status=PENDING)]");
            assertTrue(call.isPresent());
            assertEquals("searchOrders", call.get().toolName());
            assertEquals("42", call.get().get("userId"));
            assertEquals("PENDING", call.get().get("status"));
        }

        @Test
        @DisplayName("大写 TOOL 关键字也匹配")
        void parseCaseInsensitive() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst("[tool:getStoreInfo()]");
            assertTrue(call.isPresent());
            assertEquals("getStoreInfo", call.get().toolName());
        }

        @Test
        @DisplayName("只取第一个匹配（防多工具）")
        void parseFirstOnly() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst(
                    "[TOOL:getProductInfo(productId=1)] 然后 [TOOL:getStoreInfo()]");
            assertTrue(call.isPresent());
            assertEquals("getProductInfo", call.get().toolName());
        }
    }

    @Nested
    @DisplayName("无匹配场景")
    class NoMatch {

        @Test
        @DisplayName("无协议文本")
        void noProtocol() {
            assertFalse(ToolCallParser.findFirst("普通回复，没有工具调用").isPresent());
        }

        @Test
        @DisplayName("null 或空")
        void nullOrEmpty() {
            assertFalse(ToolCallParser.findFirst(null).isPresent());
            assertFalse(ToolCallParser.findFirst("").isPresent());
            assertFalse(ToolCallParser.findFirst("   ").isPresent());
        }

        @Test
        @DisplayName("工具名含非法字符不匹配")
        void invalidToolName() {
            assertFalse(ToolCallParser.findFirst("[TOOL:get-info(id=1)]").isPresent());
        }
    }

    @Nested
    @DisplayName("容错")
    class Tolerant {

        @Test
        @DisplayName("格式错误的参数片段被跳过")
        void skipMalformedPairs() {
            Optional<ParsedToolCall> call = ToolCallParser.findFirst(
                    "[TOOL:foo(valid=1, broken, also=2)]");
            assertTrue(call.isPresent());
            assertEquals("1", call.get().get("valid"));
            assertEquals("2", call.get().get("also"));
            assertNull(call.get().get("broken"));
        }
    }
}
