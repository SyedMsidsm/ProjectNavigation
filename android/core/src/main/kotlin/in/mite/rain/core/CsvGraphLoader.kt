package `in`.mite.rain.core

object CsvGraphLoader {
    fun load(roomsCsv: String, edgesCsv: String): Graph {
        val rooms = table(roomsCsv, "rooms", setOf("node_id", "node_type", "room_name", "sign_text", "aliases", "x_m", "y_m", "corridor_side", "sign_condition", "similar_doors_nearby"))
        val edges = table(edgesCsv, "edges", setOf("edge_id", "from_node", "to_node", "length_m", "bearing_deg", "corridor_id", "bidirectional", "has_stairs"))
        val nodes = rooms.mapIndexed { index, row ->
            val label = "rooms row ${index + 2}"
            Node(row.required("node_id", label), enumValue<NodeType>(row.required("node_type", label), label),
                row.getValue("room_name"), row.getValue("sign_text"), row.getValue("aliases").split('|').map(String::trim).filter(String::isNotEmpty),
                row.number("x_m", label), row.number("y_m", label),
                row.getValue("sign_condition").takeIf(String::isNotBlank)?.let { enumValue<SignCondition>(it, label) } ?: SignCondition.MISSING,
                row.getValue("similar_doors_nearby").toIntOrNull() ?: fail("$label: invalid similar_doors_nearby"),
                row.getValue("corridor_side").lowercase(), row["notes"].orEmpty())
        }
        val links = edges.mapIndexed { index, row ->
            val label = "edges row ${index + 2}"
            Edge(row.required("edge_id", label), row.required("from_node", label), row.required("to_node", label),
                row.number("length_m", label), row.number("bearing_deg", label), row.getValue("corridor_id"),
                row.boolean("bidirectional", label), row.boolean("has_stairs", label), row["notes"].orEmpty())
        }
        return Graph(nodes, links)
    }

    private fun table(csv: String, name: String, required: Set<String>): List<Map<String, String>> {
        val records = parseCsv(csv.removePrefix("\uFEFF")).filterNot { row -> row.all(String::isBlank) }
        if (records.isEmpty()) fail("$name CSV is empty")
        val header = records.first().map(String::trim)
        if (header.size != header.toSet().size || header.any(String::isBlank)) fail("$name CSV has duplicate or blank columns")
        val missing = required - header.toSet()
        if (missing.isNotEmpty()) fail("$name CSV missing columns: ${missing.joinToString()}")
        return records.drop(1).mapIndexed { index, row ->
            if (row.size != header.size) fail("$name row ${index + 2}: expected ${header.size} fields, found ${row.size}")
            header.zip(row.map(String::trim)).toMap()
        }
    }

    /** RFC 4180-style quotes, escaped quotes, CRLF and multiline fields, with malformed-input rejection. */
    fun parseCsv(source: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var quoteClosed = false
        var i = 0
        fun endField() { row += field.toString(); field.setLength(0); quoteClosed = false }
        while (i < source.length) {
            val c = source[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < source.length && source[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; quoteClosed = true }
                } else field.append(c)
            } else when (c) {
                '"' -> { if (field.isNotEmpty() || quoteClosed) fail("Unexpected quote in CSV field"); quoted = true }
                ',' -> endField()
                '\r', '\n' -> {
                    endField(); result += row; row = mutableListOf()
                    if (c == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                }
                else -> {
                    if (quoteClosed && !c.isWhitespace()) fail("Unexpected text after a quoted CSV field")
                    if (!quoteClosed) field.append(c)
                }
            }
            i++
        }
        if (quoted) fail("Unclosed quoted CSV field")
        if (field.isNotEmpty() || row.isNotEmpty() || quoteClosed || source.endsWith(',')) { endField(); result += row }
        return result
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fail("$label: invalid ${T::class.java.simpleName} '$value'")
    private fun Map<String, String>.required(key: String, label: String): String = getValue(key).takeIf(String::isNotBlank) ?: fail("$label: $key is blank")
    private fun Map<String, String>.number(key: String, label: String): Double = getValue(key).toDoubleOrNull()?.takeIf(Double::isFinite) ?: fail("$label: $key must be finite numeric data")
    private fun Map<String, String>.boolean(key: String, label: String): Boolean = when (getValue(key).lowercase()) {
        "true" -> true
        "false" -> false
        else -> fail("$label: $key must be true or false")
    }
    private fun fail(message: String): Nothing = throw GraphValidationException(listOf(message))
}
