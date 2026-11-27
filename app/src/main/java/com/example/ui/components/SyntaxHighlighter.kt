package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxColors

class CodeVisualTransformation(
    private val extension: String,
    private val colors: SyntaxColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = SyntaxHighlighter.highlight(text.text, extension, colors)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

object SyntaxHighlighter {

    private val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "from", "function", "if", "import", "in", "instanceof", "let", "new",
        "of", "return", "static", "super", "switch", "this", "throw", "try", "typeof",
        "var", "void", "while", "with", "yield", "true", "false", "null", "undefined",
        "require", "module", "exports"
    )

    private val jsBuiltins = setOf(
        "console", "process", "Buffer", "Promise", "Math", "JSON", "Object", "Array",
        "String", "Number", "Boolean", "Date", "Map", "Set", "Error", "RegExp",
        "setTimeout", "setInterval", "clearTimeout", "clearInterval", "fetch", "URL"
    )

    fun highlight(code: String, extension: String, colors: SyntaxColors): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        return when (extension.lowercase()) {
            "json" -> highlightJson(code, colors)
            "md", "markdown" -> highlightMarkdown(code, colors)
            else -> highlightJs(code, colors)
        }
    }

    private fun highlightJs(code: String, colors: SyntaxColors): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = colors.plainText), 0, code.length)

            val n = code.length
            var i = 0
            while (i < n) {
                val ch = code[i]
                when {
                    // Single-line comment
                    ch == '/' && i + 1 < n && code[i + 1] == '/' -> {
                        val start = i
                        while (i < n && code[i] != '\n') i++
                        addStyle(
                            SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic),
                            start,
                            i
                        )
                    }
                    // Multi-line comment
                    ch == '/' && i + 1 < n && code[i + 1] == '*' -> {
                        val start = i
                        i += 2
                        while (i + 1 < n && !(code[i] == '*' && code[i + 1] == '/')) i++
                        i = minOf(n, i + 2)
                        addStyle(
                            SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic),
                            start,
                            i
                        )
                    }
                    // Strings & template literals
                    ch == '\'' || ch == '"' || ch == '`' -> {
                        val quote = ch
                        val start = i
                        i++
                        while (i < n) {
                            if (code[i] == '\\' && i + 1 < n) {
                                i += 2
                                continue
                            }
                            if (code[i] == quote) {
                                i++
                                break
                            }
                            if (quote != '`' && code[i] == '\n') break
                            i++
                        }
                        addStyle(SpanStyle(color = colors.string), start, i)
                    }
                    // Numbers
                    ch.isDigit() -> {
                        val start = i
                        while (i < n && (code[i].isLetterOrDigit() || code[i] == '.' || code[i] == '_')) i++
                        addStyle(SpanStyle(color = colors.number), start, i)
                    }
                    // Identifiers & Keywords
                    ch.isLetter() || ch == '_' || ch == '$' -> {
                        val start = i
                        while (i < n && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '$')) i++
                        val word = code.substring(start, i)
                        val isFollowedByParen = i < n && code[i] == '('
                        val style = when {
                            word in jsKeywords -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)
                            word in jsBuiltins -> SpanStyle(color = colors.typeOrClass)
                            isFollowedByParen -> SpanStyle(color = colors.function)
                            word.first().isUpperCase() -> SpanStyle(color = colors.typeOrClass)
                            else -> null
                        }
                        if (style != null) {
                            addStyle(style, start, i)
                        }
                    }
                    // Punctuation / Operators
                    ch in "{}()[].,:;+-*/%=<>!&|?^~" -> {
                        addStyle(SpanStyle(color = colors.punctuation), i, i + 1)
                        i++
                    }
                    else -> i++
                }
            }
        }
    }

    private fun highlightJson(code: String, colors: SyntaxColors): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = colors.plainText), 0, code.length)

            val n = code.length
            var i = 0
            while (i < n) {
                val ch = code[i]
                when {
                    ch == '"' -> {
                        val start = i
                        i++
                        while (i < n) {
                            if (code[i] == '\\' && i + 1 < n) {
                                i += 2
                                continue
                            }
                            if (code[i] == '"') {
                                i++
                                break
                            }
                            i++
                        }
                        var lookahead = i
                        while (lookahead < n && code[lookahead].isWhitespace()) lookahead++
                        val isKey = lookahead < n && code[lookahead] == ':'
                        addStyle(
                            SpanStyle(color = if (isKey) colors.propertyKey else colors.string),
                            start,
                            i
                        )
                    }
                    ch.isDigit() || (ch == '-' && i + 1 < n && code[i + 1].isDigit()) -> {
                        val start = i
                        i++
                        while (i < n && (code[i].isDigit() || code[i] in ".eE+-")) i++
                        addStyle(SpanStyle(color = colors.number), start, i)
                    }
                    code.startsWith("true", i) -> {
                        addStyle(SpanStyle(color = colors.keyword), i, i + 4)
                        i += 4
                    }
                    code.startsWith("false", i) -> {
                        addStyle(SpanStyle(color = colors.keyword), i, i + 5)
                        i += 5
                    }
                    code.startsWith("null", i) -> {
                        addStyle(SpanStyle(color = colors.keyword), i, i + 4)
                        i += 4
                    }
                    ch in "{}[]:," -> {
                        addStyle(SpanStyle(color = colors.punctuation), i, i + 1)
                        i++
                    }
                    else -> i++
                }
            }
        }
    }

    private fun highlightMarkdown(code: String, colors: SyntaxColors): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = colors.plainText), 0, code.length)
            var idx = 0
            for (line in code.split('\n')) {
                val end = idx + line.length
                when {
                    line.trimStart().startsWith("#") -> {
                        addStyle(SpanStyle(color = colors.function, fontWeight = FontWeight.Bold), idx, end)
                    }
                    line.trimStart().startsWith("```") -> {
                        addStyle(SpanStyle(color = colors.keyword), idx, end)
                    }
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                        addStyle(SpanStyle(color = colors.propertyKey), idx, minOf(end, idx + 2))
                    }
                }
                idx = end + 1
            }
        }
    }
}
