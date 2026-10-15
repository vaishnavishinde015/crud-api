package com.example.crud.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small dependency-free helper for parsing and writing the JSON used by the API. */
public final class Json {
    private Json() {
    }

    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected content after JSON value");
        }
        return value;
    }

    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }

        Map<String, Object> object = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            object.put((String) entry.getKey(), entry.getValue());
        }
        return object;
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        writeValue(value, output);
        return output.toString();
    }

    private static void writeValue(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            writeString((String) value, output);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?>) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings.");
                }
                if (!first) {
                    output.append(',');
                }
                writeString((String) entry.getKey(), output);
                output.append(':');
                writeValue(entry.getValue(), output);
                first = false;
            }
            output.append('}');
        } else if (value instanceof Iterable<?>) {
            output.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    output.append(',');
                }
                writeValue(item, output);
                first = false;
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
        }
    }

    public static String pretty(String compact) {
        String normalized = stringify(parse(compact));
        StringBuilder output = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (inString) {
                output.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            switch (character) {
                case '"':
                    inString = true;
                    output.append(character);
                    break;
                case '{':
                case '[':
                    output.append(character);
                    char closing = character == '{' ? '}' : ']';
                    if (normalized.charAt(i + 1) == closing) {
                        output.append(normalized.charAt(++i));
                    } else {
                        indent++;
                        appendNewline(output, indent);
                    }
                    break;
                case '}':
                case ']':
                    appendNewline(output, --indent);
                    output.append(character);
                    break;
                case ',':
                    output.append(character);
                    appendNewline(output, indent);
                    break;
                case ':':
                    output.append(": ");
                    break;
                default:
                    output.append(character);
            }
        }
        return output.toString();
    }

    private static void appendNewline(StringBuilder output, int indent) {
        output.append('\n');
        for (int i = 0; i < indent; i++) {
            output.append("  ");
        }
    }

    private static void writeString(String value, StringBuilder output) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    output.append("\\\"");
                    break;
                case '\\':
                    output.append("\\\\");
                    break;
                case '\b':
                    output.append("\\b");
                    break;
                case '\f':
                    output.append("\\f");
                    break;
                case '\n':
                    output.append("\\n");
                    break;
                case '\r':
                    output.append("\\r");
                    break;
                case '\t':
                    output.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        output.append("\\u");
                        String hex = Integer.toHexString(character);
                        for (int padding = hex.length(); padding < 4; padding++) {
                            output.append('0');
                        }
                        output.append(hex);
                    } else {
                        output.append(character);
                    }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            if (input == null) {
                throw new IllegalArgumentException("JSON input cannot be null.");
            }
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw error("Expected a JSON value");
            }
            char character = input.charAt(position);
            if (character == '{') {
                return parseObject();
            }
            if (character == '[') {
                return parseArray();
            }
            if (character == '"') {
                return parseString();
            }
            if (character == 't') {
                return parseLiteral("true", Boolean.TRUE);
            }
            if (character == 'f') {
                return parseLiteral("false", Boolean.FALSE);
            }
            if (character == 'n') {
                return parseLiteral("null", null);
            }
            if (character == '-' || isDigit(character)) {
                return parseNumber();
            }
            throw error("Unexpected character");
        }

        private Map<String, Object> parseObject() {
            java.util.Map<String, Object> object = new java.util.LinkedHashMap<String, Object>();
            position++;
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || input.charAt(position) != '"') {
                    throw error("Expected a string key");
                }
                String key = parseString();
                skipWhitespace();
                require(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                require(',');
            }
        }

        private java.util.List<Object> parseArray() {
            java.util.List<Object> array = new java.util.ArrayList<Object>();
            position++;
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                require(',');
            }
        }

        private String parseString() {
            require('"');
            StringBuilder value = new StringBuilder();
            while (!isAtEnd()) {
                char character = input.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (isAtEnd()) {
                        throw error("Unterminated escape sequence");
                    }
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            value.append(escaped);
                            break;
                        case 'b':
                            value.append('\b');
                            break;
                        case 'f':
                            value.append('\f');
                            break;
                        case 'n':
                            value.append('\n');
                            break;
                        case 'r':
                            value.append('\r');
                            break;
                        case 't':
                            value.append('\t');
                            break;
                        case 'u':
                            value.append(parseUnicodeEscape());
                            break;
                        default:
                            throw error("Invalid escape sequence");
                    }
                } else if (character < 0x20) {
                    throw error("Unescaped control character in string");
                } else {
                    value.append(character);
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicodeEscape() {
            if (position + 4 > input.length()) {
                throw error("Incomplete Unicode escape");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(position++), 16);
                if (digit < 0) {
                    throw error("Invalid Unicode escape");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private Object parseNumber() {
            int start = position;
            consume('-');
            if (consume('0')) {
                if (!isAtEnd() && isDigit(input.charAt(position))) {
                    throw error("Leading zero in number");
                }
            } else {
                requireDigit();
                while (!isAtEnd() && isDigit(input.charAt(position))) {
                    position++;
                }
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                requireDigit();
                while (!isAtEnd() && isDigit(input.charAt(position))) {
                    position++;
                }
            }
            if (!isAtEnd() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                decimal = true;
                position++;
                if (!isAtEnd() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                requireDigit();
                while (!isAtEnd() && isDigit(input.charAt(position))) {
                    position++;
                }
            }
            String number = input.substring(start, position);
            try {
                if (decimal) {
                    return Double.valueOf(number);
                }
                return Long.valueOf(number);
            } catch (NumberFormatException e) {
                throw error("Number is outside the supported range");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, position)) {
                throw error("Invalid value");
            }
            position += literal.length();
            return value;
        }

        private void requireDigit() {
            if (isAtEnd() || !isDigit(input.charAt(position))) {
                throw error("Expected a digit");
            }
            position++;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            if (!isAtEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!isAtEnd()) {
                char character = input.charAt(position);
                if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                    position++;
                } else {
                    break;
                }
            }
        }

        private boolean isAtEnd() {
            return position >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + position + ".");
        }

        private boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }
    }
}
